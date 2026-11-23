package com.rmrx.marketanalyzer.marketdata.model;

import java.time.Duration;

/** Candle timeframes used inside the trading system. */
public enum Timeframe {
    M1(Duration.ofMinutes(1)),
    M5(Duration.ofMinutes(5)),
    M15(Duration.ofMinutes(15)),
    M30(Duration.ofMinutes(30)),
    H1(Duration.ofHours(1)),
    H2(Duration.ofHours(2)),
    H4(Duration.ofHours(4)),
    H6(Duration.ofHours(6)),
    H12(Duration.ofHours(12)),
    D1(Duration.ofDays(1)),
    D2(Duration.ofDays(2)),
    D3(Duration.ofDays(3)),
    W1(Duration.ofDays(7));

    private final Duration duration;

    Timeframe(Duration duration) {
        this.duration = duration;
    }

    public Duration duration() {
        return duration;
    }
}
