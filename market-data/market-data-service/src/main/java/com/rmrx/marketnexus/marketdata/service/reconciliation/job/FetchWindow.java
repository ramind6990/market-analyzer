package com.rmrx.marketnexus.marketdata.service.reconciliation.job;

import com.rmrx.marketnexus.marketdata.model.Timeframe;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * A range of candles to fetch from the exchange (one reconciliation job). Unlike a {@link ReconciliationGap}, a window may also contain
 * candles that are already stored (several nearby gaps packed together).
 *
 * @param from open time of the first candle to fetch (inclusive)
 * @param to   open time of the last candle to fetch (inclusive)
 */
public record FetchWindow(Instant from, Instant to) {

    public FetchWindow {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("Window 'to' must not be before 'from': " + from + " .. " + to);
        }
    }

    /** Number of candles of the given timeframe in this window. */
    public long candles(Timeframe timeframe) {
        return Duration.between(from, to).dividedBy(timeframe.duration()) + 1;
    }

    @Override
    public String toString() {
        return "[" + from + " .. " + to + "]";
    }
}
