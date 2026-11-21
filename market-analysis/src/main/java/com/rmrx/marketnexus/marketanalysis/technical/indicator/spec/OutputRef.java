package com.rmrx.marketnexus.marketanalysis.technical.indicator.spec;

import java.util.Objects;

/**
 * One named output of one indicator instance, e.g. {@code MACD(fast=12,...).signal}. This is the unit that is computed,
 * stored and seeded: one scalar value per bar.
 */
public record OutputRef(IndicatorSpec spec, String output) implements Source {

    public OutputRef {
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(output, "output");
        if (output.isBlank()) {
            throw new IllegalArgumentException("output name must not be blank");
        }
    }

    @Override
    public String canonical() {
        return spec.canonical() + "." + output;
    }

    @Override
    public String toString() {
        return canonical();
    }
}
