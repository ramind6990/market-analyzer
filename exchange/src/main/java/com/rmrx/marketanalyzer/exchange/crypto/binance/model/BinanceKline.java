package com.rmrx.marketanalyzer.exchange.crypto.binance.model;

import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * One Binance kline (candle). Volume is in the base asset.
 * <p>
 * Binance reports the close time as the last millisecond of the kline ({@code open + duration - 1 ms});
 * {@link #closeTime()} returns the exclusive end ({@code open + duration}) like the other exchanges.
 */
public record BinanceKline(
        String symbol,
        BinanceInterval interval,
        Instant openTime,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        BigDecimal volume) {

    public BinanceKline {
        Objects.requireNonNull(symbol, "symbol");
        Objects.requireNonNull(interval, "interval");
        Objects.requireNonNull(openTime, "openTime");
        Objects.requireNonNull(open, "open");
        Objects.requireNonNull(high, "high");
        Objects.requireNonNull(low, "low");
        Objects.requireNonNull(close, "close");
        Objects.requireNonNull(volume, "volume");
    }

    /** End of the kline (exclusive). */
    public Instant closeTime() {
        return openTime.plus(interval.duration());
    }

    public boolean isClosed(Instant now) {
        return !closeTime().isAfter(now);
    }

    /**
     * Parses one REST kline: a 12-element array {@code [openTime, open, high, low, close, volume, closeTime,
     * quoteVolume, trades, takerBuyBase, takerBuyQuote, ignore]}; prices and volumes are strings.
     */
    public static BinanceKline fromRestArray(String symbol, BinanceInterval interval, JsonNode array) {
        if (array == null || !array.isArray() || array.size() < 6) {
            throw new IllegalStateException("Malformed kline: " + array);
        }
        return new BinanceKline(symbol, interval, Instant.ofEpochMilli(array.get(0).asLong()),
                decimal(array.get(1)), decimal(array.get(2)), decimal(array.get(3)), decimal(array.get(4)),
                decimal(array.get(5)));
    }

    /** Parses the {@code k} object of a WebSocket kline event ({@code t} open time, {@code o h l c v}). */
    public static BinanceKline fromStreamObject(String symbol, BinanceInterval interval, JsonNode k) {
        if (k == null || !k.isObject() || !k.has("t")) {
            throw new IllegalStateException("Malformed kline event: " + k);
        }
        return new BinanceKline(symbol, interval, Instant.ofEpochMilli(k.get("t").asLong()),
                decimal(k.get("o")), decimal(k.get("h")), decimal(k.get("l")), decimal(k.get("c")),
                decimal(k.get("v")));
    }

    private static BigDecimal decimal(JsonNode node) {
        if (node == null || node.isNull()) {
            throw new IllegalStateException("Missing kline value");
        }
        return new BigDecimal(node.asString());
    }
}
