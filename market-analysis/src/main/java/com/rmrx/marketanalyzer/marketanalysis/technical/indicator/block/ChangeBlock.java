package com.rmrx.marketanalyzer.marketanalysis.technical.indicator.block;

import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;

/** Gain {@code max(0, x(i) - x(i-1))} or loss {@code max(0, x(i-1) - x(i))}; 0 on the first bar. Lookback 1. */
public final class ChangeBlock extends SeededIndicator {

    private final Indicator<Num> input;
    private final boolean gain;

    private ChangeBlock(Indicator<Num> input, boolean gain, Seed seed) {
        super(input.getBarSeries(), seed);
        this.input = input;
        this.gain = gain;
    }

    public static ChangeBlock gain(Indicator<Num> input, Seed seed) {
        return new ChangeBlock(input, true, seed);
    }

    public static ChangeBlock loss(Indicator<Num> input, Seed seed) {
        return new ChangeBlock(input, false, seed);
    }

    @Override
    protected double compute(int index) {
        if (index <= beginIndex()) {
            return 0;
        }
        double change = value(input, index) - value(input, index - 1);
        return Math.max(0, gain ? change : -change);
    }
}
