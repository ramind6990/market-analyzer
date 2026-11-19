package com.rmrx.marketnexus.marketdata.aggregator.model;

import java.time.Duration;
import java.time.Instant;

/**
 * Timeframes of the aggregation cascade. Each derived timeframe is built from {@link #child()} and completes after
 * {@link #expectedChildren()} closed children.
 * <p>
 * Windows follow the exchange grid: Nobitex opens days at 20:30 UTC (and hours at HH:30). All windows are aligned to
 * that anchor, i.e. {@code offset = 20:30 mod duration} — 0 for M1..M30, :30 for H1/H4, 08:30 for H12, 20:30 for D1.
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

    /** Daily grid anchor (20:30 UTC) all windows are aligned to. */
    private static final long ANCHOR_MS = Duration.ofHours(20).plusMinutes(30).toMillis();

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

    /** Start (epoch ms) of the window of this timeframe that contains {@code time}. */
    public long windowStart(Instant time) {
        long durationMs = duration.toMillis();
        long offset = ANCHOR_MS % durationMs;
        return Math.floorDiv(time.toEpochMilli() - offset, durationMs) * durationMs + offset;
    }
}
