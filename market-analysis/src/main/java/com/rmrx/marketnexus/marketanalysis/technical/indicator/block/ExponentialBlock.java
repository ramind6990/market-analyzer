package com.rmrx.marketnexus.marketanalysis.technical.indicator.block;

import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;

/**
 * Exponential smoothing {@code value(i) = value(i-1) + alpha * (input(i) - value(i-1))}, started with the first
 * input (ta4j convention). Recursive: needs only its own previous value, so a seed of one bar is enough.
 * <ul>
 *   <li>{@link #ema}: alpha = 2 / (n + 1)</li>
 *   <li>{@link #wilder}: alpha = 1 / n (Wilder / SMMA / RMA, used by RSI, ATR, ADX)</li>
 * </ul>
 */
public final class ExponentialBlock extends SeededIndicator {

    private final Indicator<Num> input;
    private final double alpha;

    private ExponentialBlock(Indicator<Num> input, double alpha, Seed seed) {
        super(input.getBarSeries(), seed);
        this.input = input;
        this.alpha = alpha;
    }

    public static ExponentialBlock ema(Indicator<Num> input, int period, Seed seed) {
        return new ExponentialBlock(input, 2.0 / (period + 1), seed);
    }

    public static ExponentialBlock wilder(Indicator<Num> input, int period, Seed seed) {
        return new ExponentialBlock(input, 1.0 / period, seed);
    }

    @Override
    protected double compute(int index) {
        if (index <= beginIndex()) {
            return value(input, index);
        }
        double previous = self(index - 1);
        return (value(input, index) - previous) * alpha + previous;
    }
}
