package com.rmrx.marketnexus.marketanalysis.job;

import com.rmrx.marketnexus.marketanalysis.seriestracker.CandleSeriesHistoryChanged;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Request to bring the indicators of a series up to date: one or more {@link CandleSeriesHistoryChanged} messages
 * merged into one (see {@link #merge}).
 *
 * @param lastCompleteOpenTime     compute up to this bar (inclusive), never past an open gap
 * @param completeLength           candles in the complete run ending at {@code lastCompleteOpenTime}
 * @param previousCompleteOpenTime complete point before the merged messages, {@code null} if unknown
 * @param lastOpenTime             newest closed candle seen at all
 */
public record SeriesTrigger(
        SeriesKey series,
        Instant lastCompleteOpenTime,
        long completeLength,
        Instant previousCompleteOpenTime,
        Instant lastOpenTime) {

    public SeriesTrigger {
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(lastCompleteOpenTime, "lastCompleteOpenTime");
        Objects.requireNonNull(lastOpenTime, "lastOpenTime");
    }

    public static SeriesTrigger of(CandleSeriesHistoryChanged change) {
        return new SeriesTrigger(new SeriesKey(change.exchange(), change.symbol(), change.timeframe()),
                change.lastCompleteOpenTime(), change.completeLength(), change.previousCompleteOpenTime(),
                change.lastOpenTime());
    }

    /**
     * One trigger covering both: newest complete point (with its length), oldest previous point (the whole span of
     * newly completed bars), newest seen candle. Example: (prev 10:00, last 10:01) + (prev 10:01, last 10:02)
     * = (prev 10:00, last 10:02).
     */
    public SeriesTrigger merge(SeriesTrigger other) {
        if (!series.equals(other.series)) {
            throw new IllegalArgumentException("different series: " + series + " / " + other.series);
        }
        SeriesTrigger newer = other.lastCompleteOpenTime.isAfter(lastCompleteOpenTime) ? other : this;
        long length = lastCompleteOpenTime.equals(other.lastCompleteOpenTime)
                ? Math.max(completeLength, other.completeLength) : newer.completeLength;
        Instant previous = previousCompleteOpenTime == null || other.previousCompleteOpenTime == null ? null
                : min(previousCompleteOpenTime, other.previousCompleteOpenTime);
        return new SeriesTrigger(series, newer.lastCompleteOpenTime, length, previous,
                max(lastOpenTime, other.lastOpenTime));
    }

    /** Whether this trigger asks for nothing beyond {@code other}. */
    public boolean coveredBy(SeriesTrigger other) {
        return !lastCompleteOpenTime.isAfter(other.lastCompleteOpenTime) && !lastOpenTime.isAfter(other.lastOpenTime);
    }

    /** Close time of the newest complete bar. */
    public Instant closeTime() {
        return lastCompleteOpenTime.plus(duration());
    }

    /** Time by which the newest complete bar should be computed: its close plus one timeframe. */
    public Instant deadline() {
        return closeTime().plus(duration());
    }

    /**
     * LIVE: the newest complete bar is the newest bar at all and closed within the last timeframe, so its result
     * is worth acting on. Otherwise BACKFILL (catch-up after a gap, late or stale data).
     */
    public boolean isLive(Instant now) {
        return lastCompleteOpenTime.equals(lastOpenTime) && !now.isAfter(deadline());
    }

    private Duration duration() {
        return series.timeframe().duration();
    }

    private static Instant min(Instant a, Instant b) {
        return a.isBefore(b) ? a : b;
    }

    private static Instant max(Instant a, Instant b) {
        return a.isAfter(b) ? a : b;
    }
}
