package com.rmrx.marketanalyzer.marketdata.service.tracking.book;

import com.rmrx.marketanalyzer.marketdata.service.tracking.tracker.SeriesKey;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Contiguous runs of closed candles per series. Pure state: no storage, no listeners.
 * <p>
 * Callers apply the candles of one series one at a time, in commit order (the series tracker takes care of that).
 * Implementations may be memory only (single instance) or shared (e.g. Redis).
 */
public interface TrackingBook {

    /** Applies one closed candle and returns what changed. */
    TrackingEvent apply(SeriesKey series, Instant openTime);

    /** Replaces the runs of a series (startup rebuild). Runs must not overlap; order does not matter. */
    void load(SeriesKey series, List<ContiguousRun> runs);

    /** Runs of a series, oldest first. */
    List<ContiguousRun> runs(SeriesKey series);

    /** Missing ranges between the runs of a series, oldest first. Nothing after the newest run. */
    List<MissingRange> missingRanges(SeriesKey series);

    /** Newest run of a series; empty if no candle was seen. */
    Optional<ContiguousRun> newestRun(SeriesKey series);

    /** All series that have at least one run. */
    Set<SeriesKey> series();
}
