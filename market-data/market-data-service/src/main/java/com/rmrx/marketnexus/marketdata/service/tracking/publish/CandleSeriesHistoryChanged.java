package com.rmrx.marketnexus.marketdata.service.tracking.publish;

import com.rmrx.marketnexus.marketdata.model.Timeframe;
import com.rmrx.marketnexus.marketdata.service.tracking.tracker.CompleteHistory;
import com.rmrx.marketnexus.marketdata.service.tracking.tracker.SeriesKey;

import java.time.Instant;
import java.util.Objects;

/**
 * The complete history of a candle series changed. Kafka message on
 * {@code market-data.kafka.topics.candle-series-tracking} (key = {@code exchange:symbol:timeframe}); consumed by
 * analysis, which keeps a copy of this record (keep both in sync).
 * <p>
 * The complete run is the contiguous history up to the first open gap; analysis never computes past it.
 *
 * @param lastCompleteOpenTime     open time of the newest candle of the complete run
 * @param completeLength           candles in the complete run
 * @param previousCompleteOpenTime {@code lastCompleteOpenTime} of the previous message, {@code null} if none
 * @param lastOpenTime             open time of the newest closed candle seen at all (may be after an open gap)
 */
public record CandleSeriesHistoryChanged(
        String exchange,
        String symbol,
        Timeframe timeframe,
        Instant lastCompleteOpenTime,
        long completeLength,
        Instant previousCompleteOpenTime,
        Instant lastOpenTime) {

    public CandleSeriesHistoryChanged {
        Objects.requireNonNull(exchange, "exchange");
        Objects.requireNonNull(symbol, "symbol");
        Objects.requireNonNull(timeframe, "timeframe");
        Objects.requireNonNull(lastCompleteOpenTime, "lastCompleteOpenTime");
        Objects.requireNonNull(lastOpenTime, "lastOpenTime");
    }

    public static CandleSeriesHistoryChanged of(SeriesKey series, CompleteHistory history) {
        return new CandleSeriesHistoryChanged(series.exchange(), series.symbol(), series.timeframe(),
                history.lastCompleteOpenTime(), history.completeLength(), history.previousCompleteOpenTime(),
                history.lastOpenTime());
    }

    /** Kafka key: one partition per series keeps its messages in order. */
    public String seriesKey() {
        return exchange + ":" + symbol + ":" + timeframe;
    }
}
