package com.rmrx.marketanalyzer.marketanalysis.seriestracker;

import com.rmrx.marketanalyzer.marketdata.model.Timeframe;

import java.time.Instant;
import java.util.Objects;

/**
 * The complete history of a candle series changed. Kafka message on
 * {@code market-analysis.kafka.topics.candle-series-tracking} (key = {@code exchange:symbol:timeframe}), published by
 * market-data-service.
 * <p>
 * The complete run is the contiguous history from the start of the tracked history up to the first open gap.
 * Analysis never computes past it. The message is a hint only: jobs re-derive what to compute from the database.
 * <p>
 * Copy of market-data-service's {@code tracking.publish.CandleSeriesHistoryChanged}; keep both in sync.
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

    /** Kafka key: one partition per series keeps its messages in order. */
    public String seriesKey() {
        return exchange + ":" + symbol + ":" + timeframe;
    }
}
