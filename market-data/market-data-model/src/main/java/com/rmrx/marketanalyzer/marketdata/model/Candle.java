package com.rmrx.marketanalyzer.marketdata.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * An exchange-neutral OHLCV candle, as stored in the database and sent over Kafka.
 * Identity: {@code (exchange, symbol, timeframe, openTime)}.
 *
 * @param exchange  exchange identifier, e.g. {@code CRYPTO_NOBITEX}
 * @param symbol    trading pair as {@code BASE/QUOTE}, e.g. {@code BTC/USDT}
 * @param timeframe candle timeframe
 * @param openTime  start of the candle (UTC)
 * @param volume    traded volume in the base asset; prices are in the quote asset
 */
public record Candle(
        String exchange,
        String symbol,
        Timeframe timeframe,
        Instant openTime,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        BigDecimal volume) {

    public Candle {
        Objects.requireNonNull(exchange, "exchange");
        Objects.requireNonNull(symbol, "symbol");
        Objects.requireNonNull(timeframe, "timeframe");
        Objects.requireNonNull(openTime, "openTime");
        Objects.requireNonNull(open, "open");
        Objects.requireNonNull(high, "high");
        Objects.requireNonNull(low, "low");
        Objects.requireNonNull(close, "close");
        Objects.requireNonNull(volume, "volume");
    }

    /** End of the candle (exclusive). */
    public Instant closeTime() {
        return openTime.plus(timeframe.duration());
    }

    /** Key that groups all candles of one market, e.g. {@code CRYPTO_NOBITEX:BTC/USDT}; used as the Kafka message key. */
    public String marketKey() {
        return exchange + ":" + symbol;
    }

    /** True when prices are consistent: high/low bound open and close, and volume is not negative. */
    public boolean isConsistent() {
        return high.compareTo(low) >= 0
                && high.compareTo(open) >= 0 && high.compareTo(close) >= 0
                && low.compareTo(open) <= 0 && low.compareTo(close) <= 0
                && volume.signum() >= 0;
    }
}
