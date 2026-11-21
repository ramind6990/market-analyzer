package com.rmrx.marketnexus.marketanalysis.technical.indicator.block;

import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;

/** Highest or lowest input over the last {@code period} bars. Lookback period - 1. */
public final class WindowExtremeBlock extends SeededIndicator {

    private final Indicator<Num> input;
    private final int period;
    private final boolean highest;

    private WindowExtremeBlock(Indicator<Num> input, int period, boolean highest, Seed seed) {
        super(input.getBarSeries(), seed);
        this.input = input;
        this.period = period;
        this.highest = highest;
    }

    public static WindowExtremeBlock highest(Indicator<Num> input, int period, Seed seed) {
        return new WindowExtremeBlock(input, period, true, seed);
    }

    public static WindowExtremeBlock lowest(Indicator<Num> input, int period, Seed seed) {
        return new WindowExtremeBlock(input, period, false, seed);
    }

    @Override
    protected double compute(int index) {
        int start = Math.max(beginIndex(), index - period + 1);
        double result = value(input, start);
        for (int i = start + 1; i <= index; i++) {
            double v = value(input, i);
            result = highest ? Math.max(result, v) : Math.min(result, v);
        }
        return result;
    }
}
