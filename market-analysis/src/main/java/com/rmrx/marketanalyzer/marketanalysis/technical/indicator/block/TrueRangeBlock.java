package com.rmrx.marketanalyzer.marketanalysis.technical.indicator.block;

import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;

/** True range {@code max(H-L, |H-Cprev|, |L-Cprev|)}; H-L on the first bar. Lookback 1. */
public final class TrueRangeBlock extends SeededIndicator {

    public TrueRangeBlock(BarSeries series, Seed seed) {
        super(series, seed);
    }

    @Override
    protected double compute(int index) {
        Bar bar = getBarSeries().getBar(index);
        double high = bar.getHighPrice().doubleValue();
        double low = bar.getLowPrice().doubleValue();
        double range = Math.abs(high - low);
        if (index <= beginIndex()) {
            return range;
        }
        double previousClose = getBarSeries().getBar(index - 1).getClosePrice().doubleValue();
        return Math.max(range, Math.max(Math.abs(high - previousClose), Math.abs(previousClose - low)));
    }
}
