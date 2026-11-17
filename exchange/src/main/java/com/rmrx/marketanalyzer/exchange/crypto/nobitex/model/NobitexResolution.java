package com.rmrx.marketanalyzer.exchange.crypto.nobitex.model;

import java.time.Duration;

/**
 * Candle resolutions supported by the Nobitex OHLC endpoint.
 * Minute resolutions (1, 5, 15, 30) are only available for the last 7 days.
 */
public enum NobitexResolution {
    MINUTE_1("1", Duration.ofMinutes(1)),
    MINUTE_5("5", Duration.ofMinutes(5)),
    MINUTE_15("15", Duration.ofMinutes(15)),
    MINUTE_30("30", Duration.ofMinutes(30)),
    HOUR_1("60", Duration.ofHours(1)),
    HOUR_3("180", Duration.ofHours(3)),
    HOUR_4("240", Duration.ofHours(4)),
    HOUR_6("360", Duration.ofHours(6)),
    HOUR_12("720", Duration.ofHours(12)),
    DAY_1("D", Duration.ofDays(1)),
    DAY_2("2D", Duration.ofDays(2)),
    DAY_3("3D", Duration.ofDays(3));

    private final String apiValue;
    private final Duration duration;

    NobitexResolution(String apiValue, Duration duration) {
        this.apiValue = apiValue;
        this.duration = duration;
    }

    /** Value sent as the {@code resolution} query parameter. */
    public String apiValue() {
        return apiValue;
    }

    public Duration duration() {
        return duration;
    }

    /** Parses an API value; {@code "1D"} is accepted as an alias of {@code "D"}. */
    public static NobitexResolution fromApiValue(String value) {
        if ("1D".equalsIgnoreCase(value)) {
            return DAY_1;
        }
        for (NobitexResolution resolution : values()) {
            if (resolution.apiValue.equalsIgnoreCase(value)) {
                return resolution;
            }
        }
        throw new IllegalArgumentException("Unknown Nobitex resolution: " + value);
    }
}
