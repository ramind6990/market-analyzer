package com.rmrx.marketnexus.marketanalysis.service;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Indicator values of one series at one bar.
 *
 * @param layoutVersion layout the values were stored with
 * @param values        output name (e.g. {@code MACD(fast=12,signal=9,slow=26,source=CLOSE).signal}) -> value;
 *                      {@code null} when not computable at that bar (NaN)
 */
public record IndicatorSnapshot(Instant openTime, long layoutVersion, Map<String, Double> values) {

    public IndicatorSnapshot {
        values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }
}
