package com.rmrx.marketanalyzer.marketanalysis.technical.indicator.block;

import org.ta4j.core.BarSeries;

import java.util.function.IntToDoubleFunction;

/**
 * Seeded node with its formula given as a lambda. The lambda receives the bar index and access to the node's own
 * values ({@code self.applyAsDouble(i - 1)} = previous value, seeded or computed), so window, stateless and recursive
 * formulas all fit.
 */
public final class LambdaBlock extends SeededIndicator {

    @FunctionalInterface
    public interface Computation {
        double compute(int index, IntToDoubleFunction self);
    }

    private final Computation computation;

    public LambdaBlock(BarSeries series, Seed seed, Computation computation) {
        super(series, seed);
        this.computation = computation;
    }

    @Override
    protected double compute(int index) {
        return computation.compute(index, this::self);
    }
}
