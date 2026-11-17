package com.rmrx.marketnexus.exchange.model;

import java.time.Duration;

/**
 * Candle timeframes. Not every exchange supports every value;
 * an unsupported one makes the call fail with {@code ExchangeException}.
 */
public enum ExchangeTimeframe {
    M1(Duration.ofMinutes(1)),
    M5(Duration.ofMinutes(5)),
    M15(Duration.ofMinutes(15)),
    M30(Duration.ofMinutes(30)),
    H1(Duration.ofHours(1)),
    H2(Duration.ofHours(2)),
    H3(Duration.ofHours(3)),
    H4(Duration.ofHours(4)),
    H6(Duration.ofHours(6)),
    H12(Duration.ofHours(12)),
    D1(Duration.ofDays(1)),
    D2(Duration.ofDays(2)),
    D3(Duration.ofDays(3)),
    W1(Duration.ofDays(7));

    private final Duration duration;

    ExchangeTimeframe(Duration duration) {
        this.duration = duration;
    }

    public Duration duration() {
        return duration;
    }
}
