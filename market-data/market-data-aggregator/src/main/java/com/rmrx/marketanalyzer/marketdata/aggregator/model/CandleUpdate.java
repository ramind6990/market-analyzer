package com.rmrx.marketanalyzer.marketdata.aggregator.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A (possibly partial) candle flowing through the cascade and published on {@code candles-derived}.
 * Derived candles are emitted progressively: {@code closed=false} on every child update, {@code closed=true} once the
 * window is complete. Downstream upserts by {@code (exchange, symbol, timeframe, openTime)}.
 *
 * @param reconciled true if the candle (or any of its children) came from REST reconciliation
 * @param closed     true if the candle is complete and will not change any more
 */
public record CandleUpdate(
        String exchange,
        String symbol,
        Timeframe timeframe,
        Instant openTime,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        BigDecimal volume,
        boolean reconciled,
        boolean closed) {

    /** Wraps a closed input candle. */
    public static CandleUpdate closedFrom(Candle candle, boolean reconciled) {
        return new CandleUpdate(candle.exchange(), candle.symbol(), candle.timeframe(), candle.openTime(),
                candle.open(), candle.high(), candle.low(), candle.close(), candle.volume(), reconciled, true);
    }

    /** Key that groups all candles of one market, e.g. {@code CRYPTO_NOBITEX:BTC/USDT}; the stages are keyed by it. */
    public String marketKey() {
        return exchange + ":" + symbol;
    }

    /** Kafka message key on the output topic. */
    public String seriesKey() {
        return marketKey() + ":" + timeframe;
    }
}
