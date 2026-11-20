package com.rmrx.marketnexus.marketdata.service.tracking.publish;

import com.rmrx.marketnexus.marketdata.service.tracking.tracker.SeriesKey;
import com.rmrx.marketnexus.marketdata.model.Timeframe;

import java.time.Instant;
import java.util.Objects;

/**
 * The complete history of a candle series changed: its newest run of closed candles grew, merged after a gap was
 * filled, or restarted after a gap. Kafka message on {@code market-data.kafka.topics.candle-series-tracking}
 * (key = {@code exchange:symbol:timeframe}); consumed by analysis.
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

    public static CandleSeriesHistoryChanged of(SeriesKey series, long newestRunLength, Instant newestOpenTime) {
        return new CandleSeriesHistoryChanged(series.exchange(), series.symbol(), series.timeframe(), newestRunLength,
                newestOpenTime);
    }

    /** Kafka key: one partition per series keeps its messages in order. */
    public String seriesKey() {
        return exchange + ":" + symbol + ":" + timeframe;
    }
}
