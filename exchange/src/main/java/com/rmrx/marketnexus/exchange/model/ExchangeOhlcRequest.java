package com.rmrx.marketnexus.exchange.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Request for OHLCV candles.
 * <p>
 * Returns candles whose open time is in {@code [from, to]}, newest first trimmed to {@code limit}.
 * To page through long histories, repeat with {@code to} set just before the oldest candle received.
 * Exchanges cap how many candles one call returns (Nobitex: 500); fewer than {@code limit} may come back.
 *
 * @param symbol    trading pair (required)
 * @param timeframe candle timeframe (required)
 * @param from      earliest open time (optional)
 * @param to        latest open time (optional; {@code null} = now)
 * @param limit     maximum number of candles, counted back from {@code to} (optional; {@code null} = exchange default)
 */
public record ExchangeOhlcRequest(
        ExchangeSymbol symbol,
        ExchangeTimeframe timeframe,
        Instant from,
        Instant to,
        Integer limit) {

    public ExchangeOhlcRequest {
        Objects.requireNonNull(symbol, "symbol");
        Objects.requireNonNull(timeframe, "timeframe");
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("from must not be after to");
        }
        if (limit != null && limit < 1) {
            throw new IllegalArgumentException("limit must be >= 1");
        }
    }

    /** The latest {@code limit} candles. */
    public static ExchangeOhlcRequest latest(ExchangeSymbol symbol, ExchangeTimeframe timeframe, int limit) {
        return new ExchangeOhlcRequest(symbol, timeframe, null, null, limit);
    }

    /** Candles opened between {@code from} and {@code to}. */
    public static ExchangeOhlcRequest range(ExchangeSymbol symbol, ExchangeTimeframe timeframe,
                                            Instant from, Instant to) {
        return new ExchangeOhlcRequest(symbol, timeframe, from, to, null);
    }
}
