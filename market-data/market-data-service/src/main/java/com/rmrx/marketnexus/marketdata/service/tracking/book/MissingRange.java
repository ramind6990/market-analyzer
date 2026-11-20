package com.rmrx.marketnexus.marketdata.service.tracking.book;

import java.time.Instant;
import java.util.Objects;

/**
 * Candles missing between two runs of the book.
 *
 * @param from open time of the first missing candle (inclusive)
 * @param to   open time of the last missing candle (inclusive)
 */
public record MissingRange(Instant from, Instant to) {

    public MissingRange {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("Range 'to' must not be before 'from': " + from + " .. " + to);
        }
    }

    /** Whether this range shares at least one open time with {@code [otherFrom, otherTo]}. */
    public boolean overlaps(Instant otherFrom, Instant otherTo) {
        return !from.isAfter(otherTo) && !to.isBefore(otherFrom);
    }

    @Override
    public String toString() {
        return "[" + from + " .. " + to + "]";
    }
}
