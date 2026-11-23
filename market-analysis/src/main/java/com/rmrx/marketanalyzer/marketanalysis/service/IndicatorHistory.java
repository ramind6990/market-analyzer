package com.rmrx.marketanalyzer.marketanalysis.service;

import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.spec.IndicatorSpec;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Stored values of one indicator of one series, newest bar first.
 *
 * @param spec effective indicator (all parameters, defaults filled in)
 */
public record IndicatorHistory(IndicatorSpec spec, List<Bar> bars) {

    public IndicatorHistory {
        bars = List.copyOf(bars);
    }

    /** @param values output name ({@code value}, {@code signal}, ...) -> value; {@code null} when not computable */
    public record Bar(Instant openTime, Map<String, Double> values) {

        public Bar {
            values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
        }
    }
}
