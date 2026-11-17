package com.rmrx.marketnexus.exchange.crypto.nobitex.model;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * Query for {@code GET /market/udf/history}.
 *
 * @param symbol     market symbol, e.g. {@code BTCUSDT} (sent upper-case)
 * @param resolution candle resolution
 * @param to         end of the range (required); sent as Unix seconds
 * @param from       start of the range (optional); ignored by the server when {@code countback} is set
 * @param countback  number of candles before {@code to} (optional, 1..{@value #MAX_COUNTBACK})
 * @param page       page of older batches (optional, starts at 1)
 */
public record NobitexOhlcRequest(
        String symbol,
        NobitexResolution resolution,
        Instant to,
        Instant from,
        Integer countback,
        Integer page) {

    public static final int MAX_COUNTBACK = 500;

    public NobitexOhlcRequest {
        Objects.requireNonNull(symbol, "symbol");
        Objects.requireNonNull(resolution, "resolution");
        Objects.requireNonNull(to, "to");
        if (symbol.isBlank()) {
            throw new IllegalArgumentException("symbol must not be blank");
        }
        symbol = symbol.strip().toUpperCase(Locale.ROOT);
        if (from != null && from.isAfter(to)) {
            throw new IllegalArgumentException("from must not be after to");
        }
        if (countback != null && (countback < 1 || countback > MAX_COUNTBACK)) {
            throw new IllegalArgumentException("countback must be between 1 and " + MAX_COUNTBACK);
        }
        if (page != null && page < 1) {
            throw new IllegalArgumentException("page must be >= 1");
        }
    }

    /** The last {@code countback} candles up to {@code to}. */
    public static NobitexOhlcRequest last(String symbol, NobitexResolution resolution, Instant to, int countback) {
        return new NobitexOhlcRequest(symbol, resolution, to, null, countback, null);
    }

    /** Candles in the range {@code [from, to]}. */
    public static NobitexOhlcRequest range(String symbol, NobitexResolution resolution, Instant from, Instant to) {
        return new NobitexOhlcRequest(symbol, resolution, to, from, null, null);
    }

    public NobitexOhlcRequest withPage(int newPage) {
        return new NobitexOhlcRequest(symbol, resolution, to, from, countback, newPage);
    }

    /** URL-encoded query string, without the leading {@code ?}. */
    public String toQueryString() {
        StringJoiner query = new StringJoiner("&");
        query.add("symbol=" + URLEncoder.encode(symbol, StandardCharsets.UTF_8));
        query.add("resolution=" + URLEncoder.encode(resolution.apiValue(), StandardCharsets.UTF_8));
        if (from != null) {
            query.add("from=" + from.getEpochSecond());
        }
        query.add("to=" + to.getEpochSecond());
        if (countback != null) {
            query.add("countback=" + countback);
        }
        if (page != null) {
            query.add("page=" + page);
        }
        return query.toString();
    }
}
