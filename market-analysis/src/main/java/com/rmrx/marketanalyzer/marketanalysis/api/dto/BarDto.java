package com.rmrx.marketanalyzer.marketanalysis.api.dto;

import java.time.Instant;
import java.util.Map;

/** @param values output name -> value; {@code null} when not computable at this bar */
public record BarDto(Instant openTime, Map<String, Double> values) {
}
