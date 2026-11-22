package com.rmrx.marketnexus.marketanalysis.job.publish;

import com.rmrx.marketnexus.marketdata.model.Timeframe;

import java.time.Instant;
import java.util.Objects;

/**
 * Indicator values of a series were stored up to {@code lastOpenTime}. Kafka message on
 * {@code market-analysis.kafka.topics.series-indicators-updated} (key = {@code exchange:symbol:timeframe}).
 * Values are read from {@code analysis.indicator_value} (names in {@code analysis.indicator_layout}).
 *
 * @param firstOpenTime first bar stored by this job
 * @param lastOpenTime  newest bar stored by this job
 * @param layoutVersion layout of the stored values
 * @param mode          LIVE: {@code lastOpenTime} is the newest bar of the series and fresh, worth acting on;
 *                      BACKFILL: catch-up / late data, for history only
 */
public record SeriesIndicatorsUpdated(
        String exchange,
        String symbol,
        Timeframe timeframe,
        Instant firstOpenTime,
        Instant lastOpenTime,
        long layoutVersion,
        Mode mode) {

    public enum Mode { LIVE, BACKFILL }

    public SeriesIndicatorsUpdated {
        Objects.requireNonNull(exchange, "exchange");
        Objects.requireNonNull(symbol, "symbol");
        Objects.requireNonNull(timeframe, "timeframe");
        Objects.requireNonNull(firstOpenTime, "firstOpenTime");
        Objects.requireNonNull(lastOpenTime, "lastOpenTime");
        Objects.requireNonNull(mode, "mode");
    }

    public String seriesKey() {
        return exchange + ":" + symbol + ":" + timeframe;
    }
}
