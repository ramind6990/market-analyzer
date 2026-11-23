package com.rmrx.marketanalyzer.marketdata.service.reconciliation.job.execution;

import com.rmrx.marketanalyzer.marketdata.service.reconciliation.gap.ReconciliationGap;
import com.rmrx.marketanalyzer.marketdata.service.reconciliation.job.FetchWindow;
import com.rmrx.marketanalyzer.marketdata.service.reconciliation.job.JobStatus;
import com.rmrx.marketanalyzer.marketdata.service.reconciliation.job.ReconciliationJob;
import com.rmrx.marketanalyzer.marketdata.service.reconciliation.job.ReconciliationJobRepository;
import com.rmrx.marketanalyzer.marketdata.service.support.CandleGrid;

import com.rmrx.marketanalyzer.marketdata.model.Candle;
import com.rmrx.marketanalyzer.marketdata.model.Timeframe;
import com.rmrx.marketanalyzer.marketdata.service.candle.CandleRepository;
import com.rmrx.marketanalyzer.marketdata.service.candle.CandleSource;
import com.rmrx.marketanalyzer.marketdata.service.candle.UpsertedCandle;
import com.rmrx.marketanalyzer.marketdata.service.config.ReconciliationProperties;
import com.rmrx.marketanalyzer.marketdata.service.tracking.tracker.SeriesKey;
import com.rmrx.marketanalyzer.marketdata.service.tracking.tracker.SeriesTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Executes ONE step of a reconciliation job: fetches one chunk (at most {@code fetch-chunk-candles}) from the job's
 * cursor, upserts it, and moves the cursor. Call it repeatedly until the job is DONE, SKIPPED or FAILED.
 * <ol>
 *   <li>Horizon re-check: if the whole window is now older than the timeframe's retention → SKIPPED; if only the
 *       cursor is → the cursor jumps to the horizon.</li>
 *   <li>Chunk = {@code [cursor, min(cursor + (chunk − 1) · tf, windowTo)]}; fetched via {@link CandleFetcher}; only
 *       candles inside the chunk are kept.</li>
 *   <li>One transaction: upsert (source POLL; inserted vs changed reported), cursor = chunk end + tf (the REQUESTED end,
 *       so missing candles at the end can't make the job loop), status DONE once past {@code windowTo}, else PENDING.</li>
 *   <li>Candles the exchange did not return are only logged (no "unavailable range" bookkeeping yet).</li>
 *   <li>After the commit, all received candles are handed to {@link SeriesTracker} (already known ones are ignored
 *       there; passing them all keeps the book right even if it missed one), then inserted/changed candles to
 *       {@link ReconciledCandlePublisher}.</li>
 *   <li>On error: attempts + 1; PENDING again after a backoff, or FAILED after {@code max-attempts}; cursor unchanged.</li>
 * </ol>
 * The watermark is not touched here: the registrar's next gap check moves it over the repaired ranges, so jobs may run
 * in any order.
 */
@Service
public class ReconciliationJobExecutor {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationJobExecutor.class);

    /** What one execution did. */
    public record ExecutionResult(long jobId, JobStatus status, FetchWindow chunk, int received,
                                  List<UpsertedCandle> written, List<ReconciliationGap> missing) {

        public long inserted() {
            return written.stream().filter(UpsertedCandle::inserted).count();
        }

        public long changed() {
            return written.size() - inserted();
        }
    }

    private final CandleFetcher fetcher;
    private final CandleRepository candleRepository;
    private final ReconciliationJobRepository jobRepository;
    private final ReconciledCandlePublisher publisher;
    private final SeriesTracker tracker;
    private final ReconciliationProperties properties;
    private final TransactionTemplate transaction;
    private final Clock clock;

    @Autowired
    public ReconciliationJobExecutor(CandleFetcher fetcher, CandleRepository candleRepository,
                                     ReconciliationJobRepository jobRepository, ReconciledCandlePublisher publisher,
                                     SeriesTracker tracker, ReconciliationProperties properties,
                                     PlatformTransactionManager transactionManager) {
        this(fetcher, candleRepository, jobRepository, publisher, tracker, properties,
                new TransactionTemplate(transactionManager), Clock.systemUTC());
    }

    ReconciliationJobExecutor(CandleFetcher fetcher, CandleRepository candleRepository,
                              ReconciliationJobRepository jobRepository, ReconciledCandlePublisher publisher,
                              SeriesTracker tracker, ReconciliationProperties properties, TransactionTemplate transaction,
                              Clock clock) {
        this.fetcher = fetcher;
        this.candleRepository = candleRepository;
        this.jobRepository = jobRepository;
        this.publisher = publisher;
        this.tracker = tracker;
        this.properties = properties;
        this.transaction = transaction;
        this.clock = clock;
    }

    public ExecutionResult execute(ReconciliationJob job) {
        Timeframe timeframe = job.timeframe();
        Instant now = clock.instant();

        // 1. Horizon re-check: the job may have waited until (part of) its window is no longer fetchable.
        Instant horizon = CandleGrid.horizon(now, properties.effectiveRetention(timeframe), job.windowFrom(), timeframe);
        if (job.windowTo().isBefore(horizon)) {
            String reason = "window older than retention horizon " + horizon;
            jobRepository.skip(job.id(), reason);
            log.warn("{}: SKIPPED, {}", job, reason);
            return new ExecutionResult(job.id(), JobStatus.SKIPPED, null, 0, List.of(), List.of());
        }
        Instant cursor = job.cursor();
        if (cursor.isBefore(horizon)) {
            log.warn("{}: [{} .. {}] is older than the retention horizon, skipped; continuing at {}", job, cursor,
                    horizon.minus(timeframe.duration()), horizon);
            cursor = horizon;
        }

        // 2. The chunk to fetch.
        Instant chunkEnd = cursor.plus(timeframe.duration().multipliedBy(properties.fetchChunkCandles() - 1L));
        if (chunkEnd.isAfter(job.windowTo())) {
            chunkEnd = job.windowTo();
        }
        FetchWindow chunk = new FetchWindow(cursor, chunkEnd);
        jobRepository.markRunning(job.id());

        try {
            List<Candle> received = fetcher.fetch(job.exchange(), job.symbol(), timeframe, chunk.from(), chunk.to())
                    .stream()
                    .filter(candle -> !candle.openTime().isBefore(chunk.from()) && !candle.openTime().isAfter(chunk.to()))
                    .toList();

            // 3. Store and move the cursor atomically.
            Instant nextCursor = chunk.to().plus(timeframe.duration());
            JobStatus status = nextCursor.isAfter(job.windowTo()) ? JobStatus.DONE : JobStatus.PENDING;
            List<UpsertedCandle> written = transaction.execute(tx -> {
                List<UpsertedCandle> upserted = candleRepository.upsertAll(received, CandleSource.POLL);
                jobRepository.advance(job.id(), nextCursor, status);
                return upserted;
            });

            // 4. What the exchange did not return (only logged for now).
            List<ReconciliationGap> missing = missingRanges(chunk, received, timeframe);
            ExecutionResult result = new ExecutionResult(job.id(), status, chunk, received.size(), written, missing);
            log(job, result, timeframe);

            // 5. Hand the reconciled candles on: tracking first, then the aggregator.
            tracker.onClosedCandles(new SeriesKey(job.exchange(), job.symbol(), timeframe),
                    received.stream().map(Candle::openTime).toList());
            publisher.publish(job, written);
            return result;
        } catch (RuntimeException e) {
            return fail(job, chunk, e, now);
        }
    }

    private ExecutionResult fail(ReconciliationJob job, FetchWindow chunk, RuntimeException error, Instant now) {
        int attempts = job.attempts() + 1;
        String message = error.getClass().getSimpleName() + ": " + error.getMessage();
        if (attempts >= properties.maxAttempts()) {
            jobRepository.recordFailure(job.id(), JobStatus.FAILED, attempts, now, message);
            log.error("{}: FAILED after {} attempt(s) on chunk {}", job, attempts, chunk, error);
            return new ExecutionResult(job.id(), JobStatus.FAILED, chunk, 0, List.of(), List.of());
        }
        Instant nextRunAt = now.plus(properties.retryDelay(attempts));
        jobRepository.recordFailure(job.id(), JobStatus.PENDING, attempts, nextRunAt, message);
        log.warn("{}: attempt {} on chunk {} failed ({}), retry at {}", job, attempts, chunk, message, nextRunAt);
        return new ExecutionResult(job.id(), JobStatus.PENDING, chunk, 0, List.of(), List.of());
    }

    /** Open times in the chunk that the exchange did not return, as ranges. */
    static List<ReconciliationGap> missingRanges(FetchWindow chunk, List<Candle> received, Timeframe timeframe) {
        Set<Instant> present = new HashSet<>();
        received.forEach(candle -> present.add(candle.openTime()));
        List<ReconciliationGap> missing = new ArrayList<>();
        Instant runStart = null;
        Instant previous = null;
        for (Instant t = chunk.from(); !t.isAfter(chunk.to()); t = t.plus(timeframe.duration())) {
            if (present.contains(t)) {
                if (runStart != null) {
                    missing.add(new ReconciliationGap(runStart, previous));
                    runStart = null;
                }
            } else if (runStart == null) {
                runStart = t;
            }
            previous = t;
        }
        if (runStart != null) {
            missing.add(new ReconciliationGap(runStart, previous));
        }
        return missing;
    }

    private static void log(ReconciliationJob job, ExecutionResult result, Timeframe timeframe) {
        log.info("{}: chunk {} requested {}, received {}, inserted {}, changed {} -> {}", job, result.chunk(),
                result.chunk().candles(timeframe), result.received(), result.inserted(), result.changed(), result.status());
        if (!result.missing().isEmpty()) {
            long count = result.missing().stream().mapToLong(gap -> gap.missingCandles(timeframe)).sum();
            log.warn("{}: exchange returned no data for {} candle(s): {}", job, count, result.missing());
        }
    }
}
