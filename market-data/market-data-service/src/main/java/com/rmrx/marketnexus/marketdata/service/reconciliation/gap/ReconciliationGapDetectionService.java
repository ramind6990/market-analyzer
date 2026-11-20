package com.rmrx.marketnexus.marketdata.service.reconciliation.gap;

import com.rmrx.marketnexus.marketdata.model.Timeframe;
import com.rmrx.marketnexus.marketdata.service.config.TrackingProperties;
import com.rmrx.marketnexus.marketdata.service.support.CandleGrid;
import com.rmrx.marketnexus.marketdata.service.tracking.gap.CandleGap;
import com.rmrx.marketnexus.marketdata.service.tracking.gap.CandleGapRepository;
import com.rmrx.marketnexus.marketdata.service.tracking.book.ContiguousRun;
import com.rmrx.marketnexus.marketdata.service.tracking.tracker.SeriesKey;
import com.rmrx.marketnexus.marketdata.service.tracking.tracker.SeriesTracker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Finds the gaps of series after given watermarks, from what tracking already knows (no scan of the candle table).
 * Read-only: the caller decides what to do with the gaps.
 * <p>
 * Checked range per symbol: open times in {@code (watermark, upTo]}, {@code upTo} = the last closed candle (one before
 * the ongoing one, on the watermark's grid). Gaps in it come from three places:
 * <ol>
 *   <li><b>before the book</b>: from the watermark to the oldest candle the {@link SeriesTracker} knows (e.g. first-run
 *       backfill of a new series; everything if the book is empty);</li>
 *   <li><b>inner</b>: unfilled {@code candle_gap} rows;</li>
 *   <li><b>trailing</b>: from the newest known candle to {@code upTo} (feed silent, or the just-closed candle has not
 *       arrived yet; harmless, repairs are upserts).</li>
 * </ol>
 * The timeframe must be tracked ({@code market-data.tracking.timeframes}), otherwise its book is empty and the whole
 * range would look missing.
 */
@Service
public class ReconciliationGapDetectionService {

    private final SeriesTracker tracker;
    private final CandleGapRepository gapRepository;
    private final TrackingProperties tracking;
    private final Clock clock;

    @Autowired
    public ReconciliationGapDetectionService(SeriesTracker tracker, CandleGapRepository gapRepository,
                                             TrackingProperties tracking) {
        this(tracker, gapRepository, tracking, Clock.systemUTC());
    }

    ReconciliationGapDetectionService(SeriesTracker tracker, CandleGapRepository gapRepository,
                                      TrackingProperties tracking, Clock clock) {
        this.tracker = tracker;
        this.gapRepository = gapRepository;
        this.tracking = tracking;
        this.clock = clock;
    }

    /**
     * Checks several symbols of one exchange and timeframe at once.
     *
     * @param watermarks symbol → open time up to which that series is already verified gap-free (not null)
     * @return one result per symbol, in the map's iteration order
     */
    public List<ReconciliationGapCheckResult> check(String exchange, Timeframe timeframe, Map<String, Instant> watermarks) {
        if (watermarks.isEmpty()) {
            return List.of();
        }
        if (!tracking.timeframes().contains(timeframe)) {
            throw new IllegalStateException(timeframe + " is reconciled but not tracked; add it to "
                    + "market-data.tracking.timeframes");
        }
        Instant now = clock.instant();
        Map<String, List<CandleGap>> rows = gapRepository.findUnfilled(exchange, timeframe).stream()
                .collect(Collectors.groupingBy(gap -> gap.series().symbol()));
        List<ReconciliationGapCheckResult> results = new ArrayList<>(watermarks.size());
        watermarks.forEach((symbol, watermark) -> results.add(checkSeries(
                new SeriesKey(exchange, symbol, timeframe), watermark, rows.getOrDefault(symbol, List.of()), now)));
        return results;
    }

    /** Checks a single symbol. */
    public ReconciliationGapCheckResult check(String exchange, String symbol, Timeframe timeframe, Instant watermark) {
        Map<String, Instant> watermarks = new HashMap<>();
        watermarks.put(symbol, watermark);
        return check(exchange, timeframe, watermarks).getFirst();
    }

    private ReconciliationGapCheckResult checkSeries(SeriesKey key, Instant watermark, List<CandleGap> rows,
                                                     Instant now) {
        Timeframe timeframe = key.timeframe();
        Duration step = timeframe.duration();
        Instant upTo = CandleGrid.alignDown(now, watermark, timeframe).minus(step);
        Instant from = watermark.plus(step);
        List<ReconciliationGap> gaps = new ArrayList<>();
        if (!from.isAfter(upTo)) {
            List<ContiguousRun> runs = tracker.runs(key);
            if (runs.isEmpty()) {
                gaps.add(new ReconciliationGap(from, upTo));
            } else {
                Instant oldest = runs.getFirst().from();
                Instant newest = runs.getLast().to();
                addClipped(gaps, from, oldest.minus(step), from, upTo);                   // before the book
                rows.forEach(row -> addClipped(gaps, row.from(), row.to(), from, upTo)); // inner
                addClipped(gaps, newest.plus(step), upTo, from, upTo);                    // trailing
            }
        }
        return new ReconciliationGapCheckResult(key.exchange(), key.symbol(), timeframe, watermark, upTo,
                merge(gaps, step));
    }

    /** Adds {@code [gapFrom, gapTo]} clipped to {@code [lower, upper]}, if anything is left. */
    private static void addClipped(List<ReconciliationGap> gaps, Instant gapFrom, Instant gapTo,
                                   Instant lower, Instant upper) {
        Instant clippedFrom = gapFrom.isBefore(lower) ? lower : gapFrom;
        Instant clippedTo = gapTo.isAfter(upper) ? upper : gapTo;
        if (!clippedFrom.isAfter(clippedTo)) {
            gaps.add(new ReconciliationGap(clippedFrom, clippedTo));
        }
    }

    /** Sorts gaps and merges overlapping or touching ones. */
    private static List<ReconciliationGap> merge(List<ReconciliationGap> gaps, Duration step) {
        List<ReconciliationGap> sorted = gaps.stream().sorted(Comparator.comparing(ReconciliationGap::from)).toList();
        List<ReconciliationGap> merged = new ArrayList<>(sorted.size());
        for (ReconciliationGap gap : sorted) {
            if (!merged.isEmpty() && !gap.from().isAfter(merged.getLast().to().plus(step))) {
                ReconciliationGap last = merged.removeLast();
                merged.add(new ReconciliationGap(last.from(), gap.to().isAfter(last.to()) ? gap.to() : last.to()));
            } else {
                merged.add(gap);
            }
        }
        return merged;
    }
}
