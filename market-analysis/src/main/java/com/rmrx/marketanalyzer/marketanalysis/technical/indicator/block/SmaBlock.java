package com.rmrx.marketanalyzer.marketanalysis.technical.indicator.block;

import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;

/** Simple moving average over the last {@code period} inputs (fewer at the series start). Lookback period - 1. */
public final class SmaBlock extends SeededIndicator {

    private final Indicator<Num> input;
    private final int period;

    public SmaBlock(Indicator<Num> input, int period, Seed seed) {
        super(input.getBarSeries(), seed);
        this.input = input;
        this.period = period;
    }

    @Override
    protected double compute(int index) {
        int start = Math.max(beginIndex(), index - period + 1);
        double sum = 0;
        for (int i = start; i <= index; i++) {
            sum += value(input, i);
        }
        return sum / (index - start + 1);
    }
}
