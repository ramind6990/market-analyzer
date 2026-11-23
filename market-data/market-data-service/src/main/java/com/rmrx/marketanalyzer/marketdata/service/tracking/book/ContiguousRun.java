package com.rmrx.marketanalyzer.marketdata.service.tracking.book;

import com.rmrx.marketanalyzer.marketdata.model.Timeframe;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Closed candles of one series with no candle missing in between.
 *
 * @param from open time of the first candle (inclusive)
 * @param to   open time of the last candle (inclusive)
 */
public record ContiguousRun(Instant from, Instant to) {

    public ContiguousRun {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("Run 'to' must not be before 'from': " + from + " .. " + to);
        }
    }

    /** Number of candles of the given timeframe in this run. */
    public long length(Timeframe timeframe) {
        return Duration.between(from, to).dividedBy(timeframe.duration()) + 1;
    }

    @Override
    public String toString() {
        return "[" + from + " .. " + to + "]";
    }
}
