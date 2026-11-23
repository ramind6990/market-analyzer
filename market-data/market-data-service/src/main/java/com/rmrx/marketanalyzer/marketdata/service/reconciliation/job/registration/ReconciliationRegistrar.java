package com.rmrx.marketanalyzer.marketdata.service.reconciliation.job.registration;

import com.rmrx.marketanalyzer.marketdata.service.reconciliation.gap.ReconciliationGap;
import com.rmrx.marketanalyzer.marketdata.service.reconciliation.gap.ReconciliationGapCheckResult;
import com.rmrx.marketanalyzer.marketdata.service.reconciliation.gap.ReconciliationGapDetectionService;
import com.rmrx.marketanalyzer.marketdata.service.reconciliation.job.FetchWindow;
import com.rmrx.marketanalyzer.marketdata.service.reconciliation.job.ReconciliationJobRepository;
import com.rmrx.marketanalyzer.marketdata.service.reconciliation.series.MarketSeriesRepository;
import com.rmrx.marketanalyzer.marketdata.service.support.CandleGrid;

import com.rmrx.marketanalyzer.marketdata.model.Timeframe;
import com.rmrx.marketanalyzer.marketdata.service.config.IngestionProperties;
import com.rmrx.marketanalyzer.marketdata.service.config.ReconciliationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Turns gaps into reconciliation jobs, per configured timeframe (M1, H1, ...). One run of a timeframe, for all symbols:
 * <ol>
 *   <li>load each series' watermark ({@code market_series});</li>
 *   <li>find gaps after the watermarks from tracking (book + {@code candle_gap}, see
 *       {@link ReconciliationGapDetectionService});</li>
 *   <li>per symbol, in one transaction: clip gaps to the timeframe's retention horizon (older parts are unrecoverable and only
 *       logged), move the watermark to just before the first recoverable gap, pack gaps into fetch windows, drop what
 *       open jobs already cover, and register the rest as PENDING jobs.</li>
 * </ol>
 * Fetching the windows is done by a worker (next step).
 */
@Service
public class ReconciliationRegistrar {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationRegistrar.class);

    /** Outcome per symbol. */
    public record Registration(String symbol, Instant watermark, List<ReconciliationGap> unrecoverable, List<FetchWindow> registered) {
    }

    private final ReconciliationGapDetectionService gapDetection;
    private final MarketSeriesRepository seriesRepository;
    private final ReconciliationJobRepository jobRepository;
    private final ReconciliationProperties reconciliation;
    private final IngestionProperties ingestion;
    private final TransactionTemplate transaction;
    private final Clock clock;

    @Autowired
    public ReconciliationRegistrar(ReconciliationGapDetectionService gapDetection, MarketSeriesRepository seriesRepository,
                                   ReconciliationJobRepository jobRepository, ReconciliationProperties reconciliation,
                                   IngestionProperties ingestion, PlatformTransactionManager transactionManager) {
        this(gapDetection, seriesRepository, jobRepository, reconciliation, ingestion,
                new TransactionTemplate(transactionManager), Clock.systemUTC());
    }

    ReconciliationRegistrar(ReconciliationGapDetectionService gapDetection, MarketSeriesRepository seriesRepository,
                            ReconciliationJobRepository jobRepository, ReconciliationProperties reconciliation,
                            IngestionProperties ingestion, TransactionTemplate transaction, Clock clock) {
        this.gapDetection = gapDetection;
        this.seriesRepository = seriesRepository;
        this.jobRepository = jobRepository;
        this.reconciliation = reconciliation;
        this.ingestion = ingestion;
        this.transaction = transaction;
        this.clock = clock;
    }

    /**
     * One registration run for every configured timeframe (smallest first) and every configured symbol.
     * Synchronized, so the startup run and the scheduler never overlap; a failing timeframe doesn't stop the others.
     */
    public synchronized void registerAll() {
        String exchange = ingestion.exchangeType().name();
        for (Timeframe timeframe : reconciliation.reconciledTimeframes()) {
            try {
                register(exchange, timeframe, ingestion.symbols());
            } catch (RuntimeException e) {
                log.error("Reconciliation registration {} {} failed", exchange, timeframe, e);
            }
        }
    }

    /** Detects gaps of one timeframe for the symbols and registers jobs for them; returns what was done per symbol. */
    public List<Registration> register(String exchange, Timeframe timeframe, List<String> symbols) {
        if (symbols.isEmpty()) {
            return List.of();
        }
        Instant now = clock.instant();
        // A series seen for the first time starts just before the retention horizon: its whole fetchable history
        // (minus the safety margin) is checked, i.e. backfilled on the first run.
        Instant initialWatermark = CandleGrid
                .horizon(now, reconciliation.effectiveRetention(timeframe), reconciliation.gridReference(timeframe),
                        timeframe)
                .minus(timeframe.duration());
        Map<String, Instant> watermarks = seriesRepository.findWatermarks(exchange, timeframe, symbols, initialWatermark);
        List<ReconciliationGapCheckResult> results = gapDetection.check(exchange, timeframe, watermarks);
        Map<String, List<FetchWindow>> openJobs = jobRepository.findOpenWindows(exchange, timeframe, symbols);

        List<Registration> registrations = new ArrayList<>();
        for (ReconciliationGapCheckResult result : results) {
            Registration registration = transaction.execute(status -> registerSeries(exchange, timeframe, result,
                    watermarks.get(result.symbol()), openJobs.getOrDefault(result.symbol(), List.of()), now));
            registrations.add(registration);
            log(exchange, timeframe, result, registration);
        }
        return registrations;
    }

    private Registration registerSeries(String exchange, Timeframe timeframe, ReconciliationGapCheckResult result,
                                        Instant oldWatermark, List<FetchWindow> openWindows, Instant now) {
        if (result.checkedAfter() == null) {
            seriesRepository.updateWatermark(exchange, result.symbol(), timeframe, oldWatermark, now);
            return new Registration(result.symbol(), oldWatermark, List.of(), List.of());
        }
        Instant horizon = CandleGrid.horizon(now, reconciliation.effectiveRetention(timeframe), result.checkedAfter(),
                timeframe);
        ReconciliationGapPlanner.Clipped clipped = ReconciliationGapPlanner.clipToHorizon(result.gaps(), horizon, timeframe);
        Instant watermark = ReconciliationGapPlanner.newWatermark(oldWatermark, result, clipped.recoverable());
        List<FetchWindow> windows = ReconciliationGapPlanner.pack(clipped.recoverable(), reconciliation.maxWindowCandles(), timeframe);
        List<FetchWindow> toRegister = ReconciliationGapPlanner.subtract(windows, openWindows, timeframe);

        seriesRepository.updateWatermark(exchange, result.symbol(), timeframe, watermark, now);
        jobRepository.insertAll(exchange, result.symbol(), timeframe, toRegister,
                window -> ReconciliationGapPlanner.priority(window, timeframe, now, reconciliation.recentWindow()));
        return new Registration(result.symbol(), watermark, clipped.unrecoverable(), toRegister);
    }

    private static void log(String exchange, Timeframe timeframe, ReconciliationGapCheckResult result, Registration registration) {
        String series = exchange + " " + result.symbol() + " " + timeframe;
        if (result.checkedAfter() == null) {
            log.info("Reconciliation {}: no candles stored", series);
            return;
        }
        log.info("Reconciliation {}: checked ({} .. {}], {} gap(s), watermark -> {}, {} job(s) registered",
                series, result.checkedAfter(), result.upTo(), result.gaps().size(), registration.watermark(),
                registration.registered().size());
        for (ReconciliationGap gap : registration.unrecoverable()) {
            log.warn("  unrecoverable at {} (older than retention): {} ({} candle(s))", timeframe, gap,
                    gap.missingCandles(timeframe));
        }
        for (FetchWindow window : registration.registered()) {
            log.info("  job window {} ({} candle(s))", window, window.candles(timeframe));
        }
    }
}
