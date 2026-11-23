package com.rmrx.marketanalyzer.marketdata.service.reconciliation.series;

import com.rmrx.marketanalyzer.marketdata.model.Timeframe;

import java.time.Instant;
import java.util.Objects;

/**
 * State of one series ({@code market_series} row).
 *
 * @param verificationWatermark open time up to which the series is verified gap-free (inclusive); {@code null} = never checked
 * @param lastCheckedAt         when the last gap check ran; {@code null} = never
 */
public record MarketSeries(
        String exchange,
        String symbol,
        Timeframe timeframe,
        Instant verificationWatermark,
        Instant lastCheckedAt) {

    public MarketSeries {
        Objects.requireNonNull(exchange, "exchange");
        Objects.requireNonNull(symbol, "symbol");
        Objects.requireNonNull(timeframe, "timeframe");
    }
}
