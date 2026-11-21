package com.rmrx.marketnexus.marketanalysis.technical.indicator.block;

import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;

/**
 * Wilder's directional movement. +DM = up move when it is positive and larger than the down move, else 0;
 * -DM the other way round. 0 on the first bar. Lookback 1.
 */
public final class DirectionalMoveBlock extends SeededIndicator {

    private final boolean plus;

    private DirectionalMoveBlock(BarSeries series, boolean plus, Seed seed) {
        super(series, seed);
        this.plus = plus;
    }

    public static DirectionalMoveBlock plus(BarSeries series, Seed seed) {
        return new DirectionalMoveBlock(series, true, seed);
    }

    public static DirectionalMoveBlock minus(BarSeries series, Seed seed) {
        return new DirectionalMoveBlock(series, false, seed);
    }

    @Override
    protected double compute(int index) {
        if (index <= beginIndex()) {
            return 0;
        }
        Bar bar = getBarSeries().getBar(index);
        Bar previous = getBarSeries().getBar(index - 1);
        double upMove = bar.getHighPrice().doubleValue() - previous.getHighPrice().doubleValue();
        double downMove = previous.getLowPrice().doubleValue() - bar.getLowPrice().doubleValue();
        if (plus) {
            return upMove > downMove && upMove > 0 ? upMove : 0;
        }
        return downMove > upMove && downMove > 0 ? downMove : 0;
    }
}
