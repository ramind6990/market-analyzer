package com.rmrx.marketnexus.marketdata.service.tracking.rebuild;

import com.rmrx.marketnexus.marketdata.service.tracking.tracker.SeriesKey;
import java.time.Instant;
import java.util.Optional;

/**
 * Lets other parts ask the startup rebuild to reach further back for a series than {@code rebuild-candles}
 * (e.g. reconciliation: back to its watermark, so the book knows everything after it).
 */
public interface TrackingLookback {

    /** Earliest open time the book of this series should know; empty = no requirement. */
    Optional<Instant> rebuildFrom(SeriesKey series);
}
