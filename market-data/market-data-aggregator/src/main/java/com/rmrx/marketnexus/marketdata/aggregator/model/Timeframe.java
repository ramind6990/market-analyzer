package com.rmrx.marketnexus.marketdata.aggregator.model;

import java.time.Duration;
import java.time.Instant;

/**
 * Timeframes of the aggregation cascade. Each derived timeframe is built from {@link #child()} and completes after
 * {@link #expectedChildren()} closed children.
 * <p>
 * Windows follow the exchange grid, given by a daily anchor per exchange: Nobitex opens days at 20:30 UTC (and hours
 * at HH:30), so its windows use {@code offset = 20:30 mod duration} — 0 for M1..M30, :30 for H1/H4, 08:30 for H12,
 * 20:30 for D1. Binance (and any other exchange) uses round UTC boundaries (anchor 00:00).
 */
public enum Timeframe {
    M1(Duration.ofMinutes(1), null, 0),
    M5(Duration.ofMinutes(5), M1, 5),
    M15(Duration.ofMinutes(15), M5, 3),
    M30(Duration.ofMinutes(30), M15, 2),
    H1(Duration.ofHours(1), M30, 2),
    H4(Duration.ofHours(4), H1, 4),
    H12(Duration.ofHours(12), H4, 3),
    D1(Duration.ofDays(1), H12, 2);

    /** Daily grid anchor of Nobitex (20:30 UTC, Tehran midnight). */
    private static final long NOBITEX_ANCHOR_MS = Duration.ofHours(20).plusMinutes(30).toMillis();

    private final Duration duration;
    private final Timeframe child;
    private final int expectedChildren;

    Timeframe(Duration duration, Timeframe child, int expectedChildren) {
        this.duration = duration;
        this.child = child;
        this.expectedChildren = expectedChildren;
    }

    public Duration duration() {
        return duration;
    }

    /** Timeframe this one is aggregated from; {@code null} for M1. */
    public Timeframe child() {
        return child;
    }

    public int expectedChildren() {
        return expectedChildren;
    }

    /** Start (epoch ms) of the window of this timeframe that contains {@code time}, on the grid of {@code exchange}. */
    public long windowStart(String exchange, Instant time) {
        long durationMs = duration.toMillis();
        long offset = anchorMs(exchange) % durationMs;
        return Math.floorDiv(time.toEpochMilli() - offset, durationMs) * durationMs + offset;
    }

    /** Daily grid anchor (ms after 00:00 UTC) of an exchange, e.g. {@code CRYPTO_NOBITEX}; 0 for round UTC days. */
    static long anchorMs(String exchange) {
        return "CRYPTO_NOBITEX".equals(exchange) ? NOBITEX_ANCHOR_MS : 0L;
    }
}
