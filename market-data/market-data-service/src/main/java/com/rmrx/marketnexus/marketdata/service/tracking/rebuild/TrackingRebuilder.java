package com.rmrx.marketnexus.marketdata.service.tracking.rebuild;

import com.rmrx.marketnexus.marketdata.service.tracking.book.ContiguousRun;
import com.rmrx.marketnexus.marketdata.service.tracking.book.TrackingBook;
import com.rmrx.marketnexus.marketdata.service.tracking.gap.CandleGap;
import com.rmrx.marketnexus.marketdata.service.tracking.gap.CandleGapRepository;
import com.rmrx.marketnexus.marketdata.service.tracking.tracker.SeriesKey;
import com.rmrx.marketnexus.marketdata.service.tracking.tracker.SeriesTracker;
import com.rmrx.marketnexus.marketdata.model.Timeframe;
import com.rmrx.marketnexus.marketdata.service.config.IngestionProperties;
import com.rmrx.marketnexus.marketdata.service.config.TrackingProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Rebuilds the {@link TrackingBook} from the database at startup, for every configured symbol and tracked timeframe.
 * <p>
 * Per series the lookback is {@code rebuild-candles} candles, or further back if a {@link TrackingLookback} asks for it
 * (reconciliation watermark) or an unfilled gap is older (so the gap can still be settled). Whatever happened while the service was down shows up by itself: the first
 * candle after the restart lands beyond the newest run and opens a gap.
 */
@Service
public class TrackingRebuilder {

    private static final Logger log = LoggerFactory.getLogger(TrackingRebuilder.class);

    private final SeriesTracker tracker;
    private final ContiguousRunQuery runQuery;
    private final CandleGapRepository gapRepository;
    private final IngestionProperties ingestion;
    private final TrackingProperties tracking;
    private final List<TrackingLookback> lookbacks;
    private final Clock clock;

    @Autowired
    public TrackingRebuilder(SeriesTracker tracker, ContiguousRunQuery runQuery, CandleGapRepository gapRepository,
                             IngestionProperties ingestion, TrackingProperties tracking,
                             List<TrackingLookback> lookbacks) {
        this(tracker, runQuery, gapRepository, ingestion, tracking, lookbacks, Clock.systemUTC());
    }

    TrackingRebuilder(SeriesTracker tracker, ContiguousRunQuery runQuery, CandleGapRepository gapRepository,
                      IngestionProperties ingestion, TrackingProperties tracking, List<TrackingLookback> lookbacks,
                      Clock clock) {
        this.tracker = tracker;
        this.runQuery = runQuery;
        this.gapRepository = gapRepository;
        this.ingestion = ingestion;
        this.tracking = tracking;
        this.lookbacks = List.copyOf(lookbacks);
        this.clock = clock;
    }

    /** Rebuilds all series; a failing series is logged and skipped (it starts empty). */
    public void rebuildAll() {
        String exchange = ingestion.exchangeType().name();
        Instant now = clock.instant();
        int series = 0;
        int runs = 0;
        for (Timeframe timeframe : tracking.timeframes()) {
            for (String symbol : ingestion.symbols()) {
                SeriesKey key = new SeriesKey(exchange, symbol, timeframe);
                try {
                    runs += rebuild(key, now);
                    series++;
                } catch (RuntimeException e) {
                    log.error("Rebuilding tracking of {} failed; it starts empty", key, e);
                }
            }
        }
        log.info("Tracking rebuilt: {} series, {} runs", series, runs);
    }

    private int rebuild(SeriesKey key, Instant now) {
        List<CandleGap> unfilled = gapRepository.findUnfilled(key);
        Instant since = now.minus(key.timeframe().duration().multipliedBy(tracking.rebuildCandles()));
        for (TrackingLookback lookback : lookbacks) {
            Optional<Instant> from = lookback.rebuildFrom(key);
            if (from.isPresent() && from.get().isBefore(since)) {
                since = from.get();
            }
        }
        if (!unfilled.isEmpty()) {
            // from the candle right before the oldest gap, so a filled gap sits inside a run and is settled
            Instant beforeOldestGap = unfilled.getFirst().from().minus(key.timeframe().duration());
            if (beforeOldestGap.isBefore(since)) {
                since = beforeOldestGap;
            }
        }
        List<ContiguousRun> runs = runQuery.findRuns(key, since);
        tracker.load(key, runs, unfilled);
        if (!runs.isEmpty()) {
            log.debug("Tracking {}: {} runs since {}, newest {}", key, runs.size(), since, runs.getLast());
        }
        return runs.size();
    }
}
