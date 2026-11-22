package com.rmrx.marketnexus.marketdata.service.tracking.tracker;

/**
 * Told by {@link SeriesTracker} when the complete history of a series changed: the complete point moved (new candle
 * on the complete run, gap filled) or the complete run got longer (backfill), and once per series after the startup
 * rebuild. Not told while only candles after an open gap arrive. Called inside the series lock, in order per series:
 * keep it fast, never block.
 */
public interface TrackingListener {

    void onCompleteHistoryChanged(SeriesKey series, CompleteHistory history);
}
