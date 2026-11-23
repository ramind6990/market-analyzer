package com.rmrx.marketanalyzer.marketdata.service.tracking.tracker;

import java.time.Instant;
import java.util.Objects;

/**
 * Complete history of a series: contiguous closed candles up to the first open (blocking) gap.
 *
 * @param lastCompleteOpenTime     open time of the newest candle of the complete history
 * @param completeLength           candles in the run ending at {@code lastCompleteOpenTime}, as far as tracking knows
 * @param previousCompleteOpenTime {@code lastCompleteOpenTime} of the previous notification, {@code null} if none
 *                                 since start
 * @param lastOpenTime             open time of the newest closed candle at all (after an open gap when there is one)
 */
public record CompleteHistory(
        Instant lastCompleteOpenTime,
        long completeLength,
        Instant previousCompleteOpenTime,
        Instant lastOpenTime) {

    public CompleteHistory {
        Objects.requireNonNull(lastCompleteOpenTime, "lastCompleteOpenTime");
        Objects.requireNonNull(lastOpenTime, "lastOpenTime");
    }

    /** Same complete point and length (the previous point and the newest candle do not count). */
    boolean sameCompleteRun(CompleteHistory other) {
        return other != null && lastCompleteOpenTime.equals(other.lastCompleteOpenTime)
                && completeLength == other.completeLength;
    }
}
