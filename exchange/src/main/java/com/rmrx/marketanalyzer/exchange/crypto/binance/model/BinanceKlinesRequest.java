package com.rmrx.marketanalyzer.exchange.crypto.binance.model;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Objects;

/**
 * Query of {@code GET /api/v3/klines}.
 * <p>
 * Binance semantics: without {@code startTime}/{@code endTime} the newest klines are returned; with {@code startTime}
 * the result starts there (oldest first); with only {@code endTime} the newest {@code limit} klines up to it.
 * Results are always in chronological order.
 *
 * @param symbol    e.g. {@code BTCUSDT} (required)
 * @param interval  kline interval (required)
 * @param startTime earliest open time (optional)
 * @param endTime   latest time (optional)
 * @param limit     1..{@value #MAX_LIMIT}; {@code null} = Binance default (500)
 */
public record BinanceKlinesRequest(
        String symbol,
        BinanceInterval interval,
        Instant startTime,
        Instant endTime,
        Integer limit) {

    public static final int MAX_LIMIT = 1000;

    public BinanceKlinesRequest {
        Objects.requireNonNull(symbol, "symbol");
        Objects.requireNonNull(interval, "interval");
        if (limit != null && (limit < 1 || limit > MAX_LIMIT)) {
            throw new IllegalArgumentException("limit must be in 1.." + MAX_LIMIT);
        }
        if (startTime != null && endTime != null && startTime.isAfter(endTime)) {
            throw new IllegalArgumentException("startTime must not be after endTime");
        }
    }

    public String toQueryString() {
        StringBuilder query = new StringBuilder()
                .append("symbol=").append(URLEncoder.encode(symbol, StandardCharsets.UTF_8))
                .append("&interval=").append(interval.apiValue());
        if (startTime != null) {
            query.append("&startTime=").append(startTime.toEpochMilli());
        }
        if (endTime != null) {
            query.append("&endTime=").append(endTime.toEpochMilli());
        }
        if (limit != null) {
            query.append("&limit=").append(limit);
        }
        return query.toString();
    }
}
