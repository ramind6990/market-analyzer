package com.rmrx.marketanalyzer.marketanalysis.job.publish;

import com.rmrx.marketanalyzer.marketdata.model.Timeframe;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Indicator values of a series were stored up to {@code lastOpenTime}. Kafka message on
 * {@code market-analysis.kafka.topics.series-indicators-updated} (key = {@code exchange:symbol:timeframe}).
 * <p>
 * {@code values} holds every public indicator output of the newest bar ({@code lastOpenTime}), keyed by its canonical
 * name, e.g. {@code RSI(period=14,source=CLOSE).value}; {@code null} where the value is not a number. Older bars of the
 * range (a backfill job stores up to thousands) are not included, to keep the message small; they are readable from
 * {@code analysis.indicator_value} / the analysis REST API.
 *
 * @param firstOpenTime first bar stored by this job
 * @param lastOpenTime  newest bar stored by this job
 * @param layoutVersion layout of the stored values
 * @param mode          LIVE: {@code lastOpenTime} is the newest bar of the series and fresh, worth acting on;
 *                      BACKFILL: catch-up / late data, for history only
 * @param values        public indicator outputs of the bar at {@code lastOpenTime}, name -> value, in layout order
 */
public record SeriesIndicatorsUpdated(
        String exchange,
        String symbol,
        Timeframe timeframe,
        Instant firstOpenTime,
        Instant lastOpenTime,
        long layoutVersion,
        Mode mode,
        Map<String, Double> values) {

    public enum Mode { LIVE, BACKFILL }

    public SeriesIndicatorsUpdated {
        Objects.requireNonNull(exchange, "exchange");
        Objects.requireNonNull(symbol, "symbol");
        Objects.requireNonNull(timeframe, "timeframe");
        Objects.requireNonNull(firstOpenTime, "firstOpenTime");
        Objects.requireNonNull(lastOpenTime, "lastOpenTime");
        Objects.requireNonNull(mode, "mode");
        values = values == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    public String seriesKey() {
        return exchange + ":" + symbol + ":" + timeframe;
    }

    /** Short form for logs (without the values). */
    @Override
    public String toString() {
        return "SeriesIndicatorsUpdated[" + seriesKey() + ", " + firstOpenTime + " .. " + lastOpenTime + ", layout "
                + layoutVersion + ", " + mode + ", " + values.size() + " value(s)]";
    }
}
