package com.rmrx.marketnexus.marketanalysis.technical.indicator.series;

import com.rmrx.marketnexus.marketdata.model.Candle;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.num.DoubleNumFactory;

import java.util.List;

/** Converts candles of one series into a ta4j bar series ({@code DoubleNum}). */
public final class BarSeriesFactory {

    private BarSeriesFactory() {
    }

    /** Candles must belong to one series and be ordered by open time without duplicates. */
    public static BarSeries create(List<Candle> candles) {
        String name = candles.isEmpty() ? "empty"
                : candles.getFirst().marketKey() + ":" + candles.getFirst().timeframe();
        BarSeries series = new BaseBarSeriesBuilder()
                .withName(name)
                .withNumFactory(DoubleNumFactory.getInstance())
                .build();
        Candle previous = null;
        for (Candle candle : candles) {
            if (previous != null && (!candle.marketKey().equals(previous.marketKey())
                    || candle.timeframe() != previous.timeframe()
                    || !candle.openTime().isAfter(previous.openTime()))) {
                throw new IllegalArgumentException("candles not of one series or not ascending at " + candle);
            }
            series.barBuilder()
                    .timePeriod(candle.timeframe().duration())
                    .endTime(candle.closeTime())
                    .openPrice(candle.open())
                    .highPrice(candle.high())
                    .lowPrice(candle.low())
                    .closePrice(candle.close())
                    .volume(candle.volume())
                    .add();
            previous = candle;
        }
        return series;
    }
}
