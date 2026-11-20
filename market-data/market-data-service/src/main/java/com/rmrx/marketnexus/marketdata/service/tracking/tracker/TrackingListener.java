package com.rmrx.marketnexus.marketdata.service.tracking.tracker;

import java.time.Instant;

/**
 * Told by {@link SeriesTracker} when the newest run of a series changed (grew, merged after a gap fill, or restarted
 * after a gap). Called inside the series lock, in order per series: keep it fast, never block.
 */
public interface TrackingListener {

    /**
     * @param series           the series
     * @param newestRunLength  candles in the newest run (complete history ending at {@code newestOpenTime})
     * @param newestOpenTime   open time of the latest closed candle of the series
     */
    void onNewestRunChanged(SeriesKey series, long newestRunLength, Instant newestOpenTime);
}
