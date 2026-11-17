package com.rmrx.marketanalyzer.exchange.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * One OHLCV candle. Prices are in the quote asset; volume is in the base asset.
 *
 * @param openTime  start of the candle
 * @param closeTime end of the candle (exclusive)
 * @param closed    {@code false} when the candle was still forming at the time of the request
 */
public record ExchangeCandle(
        Instant openTime,
        Instant closeTime,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        BigDecimal volume,
        boolean closed) {

    public ExchangeCandle {
        Objects.requireNonNull(openTime, "openTime");
        Objects.requireNonNull(closeTime, "closeTime");
        Objects.requireNonNull(open, "open");
        Objects.requireNonNull(high, "high");
        Objects.requireNonNull(low, "low");
        Objects.requireNonNull(close, "close");
        Objects.requireNonNull(volume, "volume");
    }
}
