package com.rmrx.marketanalyzer.exchange.crypto.nobitex.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * One OHLCV candle as returned by Nobitex.
 * Prices are in the quote currency (USDT for {@code BTCUSDT}); volume is in the base currency.
 * Note: Nobitex hourly candles open at HH:30 UTC (hours aligned to Tehran time).
 *
 * @param openTime start of the candle
 */
public record NobitexCandle(
        String symbol,
        NobitexResolution resolution,
        Instant openTime,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        BigDecimal volume) {

    public NobitexCandle {
        Objects.requireNonNull(symbol, "symbol");
        Objects.requireNonNull(resolution, "resolution");
        Objects.requireNonNull(openTime, "openTime");
        Objects.requireNonNull(open, "open");
        Objects.requireNonNull(high, "high");
        Objects.requireNonNull(low, "low");
        Objects.requireNonNull(close, "close");
        Objects.requireNonNull(volume, "volume");
    }

    /** End of the candle (exclusive): {@code openTime + resolution}. */
    public Instant closeTime() {
        return openTime.plus(resolution.duration());
    }

    /** True when the candle has finished at {@code now}; the newest candle in a response is usually still forming. */
    public boolean isClosed(Instant now) {
        return !closeTime().isAfter(now);
    }
}
