package com.rmrx.marketanalyzer.marketanalysis.technical.indicator.block;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Base of every computed node. Generic seeding: for bars covered by the {@link Seed} the stored value is returned,
 * otherwise {@link #compute(int)} runs.
 * <p>
 * ta4j calls {@code calculate(i)} on a cache miss, also for the recursive {@code getValue(i - 1)} an indicator makes
 * on itself. Because {@code calculate} is final here, the recursion of every subclass ends at the seed, whatever its
 * formula is. Values are computed as {@code double} (the series uses {@code DoubleNum}).
 * <p>
 * Deep recursion is avoided like ta4j's {@code RecursiveCachedIndicator} does, by filling the cache in bar order
 * first, but the fill starts after the seed (or after the last cached bar), never at bar 0: bars before the seed may
 * not be computable in a seeded job.
 */
public abstract class SeededIndicator extends CachedIndicator<Num> {

    private final Seed seed;
    private final NumFactory numFactory;

    protected SeededIndicator(BarSeries series, Seed seed) {
        super(series);
        this.seed = seed;
        this.numFactory = series.numFactory();
    }

    @Override
    public Num getValue(int index) {
        int start;
        if (!seed.isEmpty()) {
            start = Math.max(highestResultIndex + 1, seed.lastIndex() + 1);
        } else {
            start = highestResultIndex >= 0 ? highestResultIndex + 1 : index;
        }
        for (int i = start; i < index; i++) {
            super.getValue(i);
        }
        return super.getValue(index);
    }

    @Override
    protected final Num calculate(int index) {
        double value = seed.covers(index) ? seed.value(index) : compute(index);
        return numFactory.numOf(value);
    }

    /** Value at {@code index}; only called for bars not covered by the seed. */
    protected abstract double compute(int index);

    /** Own value at {@code index} (seeded or cached). */
    protected double self(int index) {
        return getValue(index).doubleValue();
    }

    protected static double value(Indicator<Num> indicator, int index) {
        return indicator.getValue(index).doubleValue();
    }

    protected int beginIndex() {
        return getBarSeries().getBeginIndex();
    }

    @Override
    public int getCountOfUnstableBars() {
        return 0;
    }
}
