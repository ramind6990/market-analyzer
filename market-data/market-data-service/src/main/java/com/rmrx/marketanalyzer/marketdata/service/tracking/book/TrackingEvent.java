package com.rmrx.marketanalyzer.marketdata.service.tracking.book;

import java.util.Objects;

/**
 * What one closed candle did to the book of its series.
 *
 * @param kind             what happened
 * @param newRange         the missing range this candle revealed ({@link Kind#GAP_OPENED}, {@link Kind#BACKFILL_DETACHED});
 *                         {@code null} otherwise
 * @param newestRun        newest run after the change; {@code null} only for {@link Kind#MISALIGNED} on an empty book
 */
public record TrackingEvent(Kind kind, MissingRange newRange, ContiguousRun newestRun) {

    public enum Kind {
        /** First candle of an empty series: starts the first run. */
        FIRST,
        /** Next candle after the newest run: the newest run grows. */
        EXTENDED,
        /** Candle beyond the next expected one: new newest run, missing range in between. */
        GAP_OPENED,
        /** Candle at the edge of a missing range between two runs: the range shrinks. */
        RANGE_SHRUNK,
        /** Last candle of a missing range: the two runs around it merge. */
        RANGE_CLOSED,
        /** Candle inside a missing range, touching neither run: becomes a run of its own, splitting the range. */
        RANGE_SPLIT,
        /** Candle right before the oldest run: the oldest run grows backwards. */
        BACKFILL,
        /** Candle older than the oldest run, not touching it: new oldest run, missing range in between. */
        BACKFILL_DETACHED,
        /** Candle already inside a run (redelivery, reconnect, re-fetch): nothing changes. */
        DUPLICATE,
        /** Candle not on the timeframe grid of the series: ignored. */
        MISALIGNED
    }

    public TrackingEvent {
        Objects.requireNonNull(kind, "kind");
    }

    /** Whether a missing range got smaller or disappeared. */
    public boolean rangeFilled() {
        return kind == Kind.RANGE_SHRUNK || kind == Kind.RANGE_CLOSED || kind == Kind.RANGE_SPLIT;
    }
}
