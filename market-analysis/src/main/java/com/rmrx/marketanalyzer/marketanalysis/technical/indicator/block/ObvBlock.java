package com.rmrx.marketanalyzer.marketanalysis.technical.indicator.block;

import org.ta4j.core.BarSeries;

/**
 * On-balance volume: running total, + volume on an up close, - volume on a down close. Cumulative: the absolute level
 * depends on the anchor (first computed bar); seeding continues the same anchor. Lookback 1.
 */
public final class ObvBlock extends SeededIndicator {

    public ObvBlock(BarSeries series, Seed seed) {
        super(series, seed);
    }

    @Override
    protected double compute(int index) {
        if (index <= beginIndex()) {
            return 0;
        }
        double close = getBarSeries().getBar(index).getClosePrice().doubleValue();
        double previousClose = getBarSeries().getBar(index - 1).getClosePrice().doubleValue();
        double volume = getBarSeries().getBar(index).getVolume().doubleValue();
        double previous = self(index - 1);
        if (close > previousClose) {
            return previous + volume;
        }
        if (close < previousClose) {
            return previous - volume;
        }
        return previous;
    }
}
