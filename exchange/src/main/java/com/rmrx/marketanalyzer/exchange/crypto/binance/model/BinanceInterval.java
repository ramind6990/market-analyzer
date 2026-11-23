package com.rmrx.marketanalyzer.exchange.crypto.binance.model;

import java.time.Duration;

/**
 * Kline intervals of the Binance spot API that map onto the generic timeframes. Binance also offers 1s, 3m, 8h and 1M,
 * which have no generic counterpart. All intervals are aligned to UTC.
 */
public enum BinanceInterval {
    MINUTE_1("1m", Duration.ofMinutes(1)),
    MINUTE_5("5m", Duration.ofMinutes(5)),
    MINUTE_15("15m", Duration.ofMinutes(15)),
    MINUTE_30("30m", Duration.ofMinutes(30)),
    HOUR_1("1h", Duration.ofHours(1)),
    HOUR_2("2h", Duration.ofHours(2)),
    HOUR_4("4h", Duration.ofHours(4)),
    HOUR_6("6h", Duration.ofHours(6)),
    HOUR_12("12h", Duration.ofHours(12)),
    DAY_1("1d", Duration.ofDays(1)),
    DAY_3("3d", Duration.ofDays(3)),
    WEEK_1("1w", Duration.ofDays(7));

    private final String apiValue;
    private final Duration duration;

    BinanceInterval(String apiValue, Duration duration) {
        this.apiValue = apiValue;
        this.duration = duration;
    }

    /** Value of the {@code interval} query parameter and of the stream name suffix (case-sensitive). */
    public String apiValue() {
        return apiValue;
    }

    public Duration duration() {
        return duration;
    }

    public static BinanceInterval fromApiValue(String value) {
        for (BinanceInterval interval : values()) {
            if (interval.apiValue.equals(value)) {
                return interval;
            }
        }
        throw new IllegalArgumentException("Unknown Binance interval: " + value);
    }
}
