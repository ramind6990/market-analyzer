package com.rmrx.marketnexus.marketanalysis.technical.indicator.block;

import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;

/** Population standard deviation over the last {@code period} inputs (two-pass, numerically stable). */
public final class StdDevBlock extends SeededIndicator {

    private final Indicator<Num> input;
    private final int period;

    public StdDevBlock(Indicator<Num> input, int period, Seed seed) {
        super(input.getBarSeries(), seed);
        this.input = input;
        this.period = period;
    }

    @Override
    protected double compute(int index) {
        int start = Math.max(beginIndex(), index - period + 1);
        int count = index - start + 1;
        double sum = 0;
        for (int i = start; i <= index; i++) {
            sum += value(input, i);
        }
        double mean = sum / count;
        double squares = 0;
        for (int i = start; i <= index; i++) {
            double diff = value(input, i) - mean;
            squares += diff * diff;
        }
        return Math.sqrt(squares / count);
    }
}
