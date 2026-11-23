package com.rmrx.marketanalyzer.marketanalysis.technical.indicator.block;

import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.spec.PriceField;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.AbstractIndicator;
import org.ta4j.core.num.Num;

/** A price field read straight from the candles. Not cached, not stored, never seeded. */
public final class PriceIndicator extends AbstractIndicator<Num> {

    private final PriceField field;

    public PriceIndicator(BarSeries series, PriceField field) {
        super(series);
        this.field = field;
    }

    @Override
    public Num getValue(int index) {
        Bar bar = getBarSeries().getBar(index);
        return switch (field) {
            case OPEN -> bar.getOpenPrice();
            case HIGH -> bar.getHighPrice();
            case LOW -> bar.getLowPrice();
            case CLOSE -> bar.getClosePrice();
            case VOLUME -> bar.getVolume();
            case HL2 -> number((d(bar.getHighPrice()) + d(bar.getLowPrice())) / 2);
            case HLC3 -> number((d(bar.getHighPrice()) + d(bar.getLowPrice()) + d(bar.getClosePrice())) / 3);
            case OHLC4 -> number((d(bar.getOpenPrice()) + d(bar.getHighPrice()) + d(bar.getLowPrice())
                    + d(bar.getClosePrice())) / 4);
        };
    }

    public PriceField field() {
        return field;
    }

    private Num number(double value) {
        return getBarSeries().numFactory().numOf(value);
    }

    private static double d(Num num) {
        return num.doubleValue();
    }

    @Override
    public int getCountOfUnstableBars() {
        return 0;
    }
}
