package com.rmrx.marketanalyzer.marketdata.service.tracking.gap;

import com.rmrx.marketanalyzer.marketdata.service.tracking.tracker.SeriesKey;
import java.time.Instant;
import java.util.Objects;

/**
 * One {@code candle_gap} row.
 *
 * @param from     open time of the first missing candle (inclusive)
 * @param to       open time of the last missing candle (inclusive)
 * @param filledAt when all candles of the gap were stored; {@code null} while unfilled
 */
public record CandleGap(
        long id,
        SeriesKey series,
        Instant from,
        Instant to,
        boolean filled,
        Instant detectedAt,
        Instant filledAt) {

    public CandleGap {
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("Gap 'to' must not be before 'from': " + from + " .. " + to);
        }
    }
}
