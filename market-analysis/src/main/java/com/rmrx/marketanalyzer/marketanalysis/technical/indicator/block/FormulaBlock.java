package com.rmrx.marketanalyzer.marketanalysis.technical.indicator.block;

import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;

import java.util.List;

/**
 * Stateless function of the current values of its inputs, e.g. MACD line = fast EMA - slow EMA,
 * Bollinger upper = middle + k * stddev. Lookback 0.
 */
public final class FormulaBlock extends SeededIndicator {

    @FunctionalInterface
    public interface Formula {
        double apply(double[] inputs);
    }

    private final List<Indicator<Num>> inputs;
    private final Formula formula;

    private FormulaBlock(List<Indicator<Num>> inputs, Formula formula, Seed seed) {
        super(inputs.getFirst().getBarSeries(), seed);
        this.inputs = inputs;
        this.formula = formula;
    }

    @SafeVarargs
    public static FormulaBlock of(Seed seed, Formula formula, Indicator<Num>... inputs) {
        if (inputs.length == 0) {
            throw new IllegalArgumentException("formula needs at least one input");
        }
        return new FormulaBlock(List.of(inputs), formula, seed);
    }

    @Override
    protected double compute(int index) {
        double[] values = new double[inputs.size()];
        for (int i = 0; i < values.length; i++) {
            values[i] = value(inputs.get(i), index);
        }
        return formula.apply(values);
    }
}
