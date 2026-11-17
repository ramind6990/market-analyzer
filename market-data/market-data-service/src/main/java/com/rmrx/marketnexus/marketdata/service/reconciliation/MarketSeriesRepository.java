package com.rmrx.marketnexus.marketdata.service.reconciliation;

import com.rmrx.marketnexus.marketdata.model.Timeframe;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Storage of {@link MarketSeries} rows. */
public interface MarketSeriesRepository {

    /**
     * Watermarks of several series of one exchange and timeframe, creating rows that don't exist yet.
     *
     * @return symbol → watermark ({@code null} = never verified), in the order of {@code symbols}
     */
    Map<String, Instant> findWatermarks(String exchange, Timeframe timeframe, List<String> symbols);

    /** Returns the series, creating it (never checked, no watermark) if it does not exist yet. */
    MarketSeries findOrCreate(String exchange, String symbol, Timeframe timeframe);

    /** Stores the result of a gap check: the new watermark (may be {@code null}) and the check time. */
    void updateWatermark(String exchange, String symbol, Timeframe timeframe, Instant watermark, Instant checkedAt);
}
