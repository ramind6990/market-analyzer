package com.rmrx.marketnexus.marketdata.service.reconciliation;

import com.rmrx.marketnexus.marketdata.model.Timeframe;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * A run of missing candles.
 *
 * @param from open time of the first missing candle (inclusive)
 * @param to   open time of the last missing candle (inclusive)
 */
public record Gap(Instant from, Instant to) {

    public Gap {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("Gap 'to' must not be before 'from': " + from + " .. " + to);
        }
    }

    /** Number of missing candles of the given timeframe in this gap. */
    public long missingCandles(Timeframe timeframe) {
        return Duration.between(from, to).dividedBy(timeframe.duration()) + 1;
    }

    @Override
    public String toString() {
        return "[" + from + " .. " + to + "]";
    }
}
