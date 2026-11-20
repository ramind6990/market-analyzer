package com.rmrx.marketnexus.marketanalysis.seriestracker;

import com.rmrx.marketnexus.marketdata.model.Timeframe;

import java.time.Instant;
import java.util.Objects;

/**
 * The complete history of a candle series changed: its newest run of closed candles grew, merged after a gap was
 * filled, or restarted after a gap. Kafka message on {@code market-analysis.kafka.topics.candle-series-tracking}
 * (key = {@code exchange:symbol:timeframe}), published by market-data-service.
 * <p>
 * Copy of market-data-service's {@code tracking.publish.CandleSeriesHistoryChanged}; keep both in sync.
 *
 * @param newestRunLength candles in the newest run: complete history ending at {@code newestOpenTime}
 * @param newestOpenTime  open time of the latest closed candle of the series
 */
public record CandleSeriesHistoryChanged(
        String exchange,
        String symbol,
        Timeframe timeframe,
        long newestRunLength,
        Instant newestOpenTime) {

    public CandleSeriesHistoryChanged {
        Objects.requireNonNull(exchange, "exchange");
        Objects.requireNonNull(symbol, "symbol");
        Objects.requireNonNull(timeframe, "timeframe");
        Objects.requireNonNull(newestOpenTime, "newestOpenTime");
    }

    /** Kafka key: one partition per series keeps its messages in order. */
    public String seriesKey() {
        return exchange + ":" + symbol + ":" + timeframe;
    }
}
