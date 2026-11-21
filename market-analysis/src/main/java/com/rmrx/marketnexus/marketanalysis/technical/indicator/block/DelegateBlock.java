package com.rmrx.marketnexus.marketanalysis.technical.indicator.block;

import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;

/**
 * Seeded node that takes its new values from a ta4j indicator. Only for ta4j indicators that are pure windows (no
 * recursion anywhere inside, plain {@code CachedIndicator}s): their value at a bar depends only on the declared lookback,
 * so they need no seed and never read bars before it. Boolean indicators become 1 / 0.
 */
public final class DelegateBlock extends SeededIndicator {

    private final Indicator<?> delegate;

    public DelegateBlock(Indicator<?> delegate, Seed seed) {
        super(delegate.getBarSeries(), seed);
        this.delegate = delegate;
    }

    @Override
    protected double compute(int index) {
        Object value = delegate.getValue(index);
        if (value instanceof Boolean b) {
            return b ? 1 : 0;
        }
        Num num = (Num) value;
        return num.isNaN() ? Double.NaN : num.doubleValue();
    }
}
