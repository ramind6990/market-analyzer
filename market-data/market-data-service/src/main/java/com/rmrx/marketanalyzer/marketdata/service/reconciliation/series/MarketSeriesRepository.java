package com.rmrx.marketanalyzer.marketdata.service.reconciliation.series;

import com.rmrx.marketanalyzer.marketdata.model.Timeframe;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Storage of {@link MarketSeries} rows. */
public interface MarketSeriesRepository {

    /**
     * Watermarks of several series of one exchange and timeframe. Series seen for the first time are created with
     * {@code initialWatermark}, so their first gap check starts there (e.g. at the retention horizon = first-run backfill);
     * existing series keep their watermark (a NULL one, from rows created earlier, is treated as {@code initialWatermark}).
     *
     * @return symbol → watermark, in the order of {@code symbols}
     */
    Map<String, Instant> findWatermarks(String exchange, Timeframe timeframe, List<String> symbols, Instant initialWatermark);

    /** Watermark of a series; empty if the series is unknown or was never checked. Read-only. */
    Optional<Instant> findWatermark(String exchange, String symbol, Timeframe timeframe);

    /** Returns the series, creating it (never checked, no watermark) if it does not exist yet. */
    MarketSeries findOrCreate(String exchange, String symbol, Timeframe timeframe);

    /** Stores the result of a gap check: the new watermark (may be {@code null}) and the check time. */
    void updateWatermark(String exchange, String symbol, Timeframe timeframe, Instant watermark, Instant checkedAt);
}
