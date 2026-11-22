package com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.custom;

import com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.IndicatorDefinition;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.OutputDef;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;

import java.util.List;
import java.util.Map;

import static com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.ComputationModel.RECURSIVE;
import static com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.ComputationModel.STATELESS;
import static com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.DefinitionBuilder.define;
import static com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.IndicatorFamily.PRICE_TRANSFORM;
import static com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.type.DefinitionSupport.*;

/** Price transforms without a ta4j indicator. */
public final class CustomPriceTransformIndicators {

    public static final String HEIKIN_ASHI = "HEIKIN_ASHI";

    private CustomPriceTransformIndicators() {
    }

    public static List<IndicatorDefinition> definitions() {
        return List.of(heikinAshi());
    }

    /**
     * Heikin-Ashi candle (pandas-ta {@code ha}): close = OHLC4, open = (previous HA open + previous HA close) / 2
     * (first bar: (open + close) / 2), high = max(high, HA open, HA close), low = min(low, HA open, HA close).
     * The HA open halves its distance to the price each bar, so it is converged after ~10 bars.
     */
    static IndicatorDefinition heikinAshi() {
        return define(HEIKIN_ASHI).description("Heikin-Ashi candle").family(PRICE_TRANSFORM)
                .models(STATELESS, RECURSIVE).params()
                .outputs(OutputDef.publicOutput("open", "HA open"), OutputDef.publicOutput("high", "HA high"),
                        OutputDef.publicOutput("low", "HA low"), OutputDef.publicOutput("close", "HA close (OHLC4)"))
                .lookback(s -> 1).warmup(s -> 10)
                .build((c, s) -> {
                    BarSeries series = c.series();
                    int begin = series.getBeginIndex();
                    Indicator<Num> haClose = lambda(c, s, "close", (i, self) ->
                            (open(series, i) + high(series, i) + low(series, i) + close(series, i)) / 4);
                    Indicator<Num> haOpen = lambda(c, s, "open", (i, self) -> i <= begin
                            ? (open(series, i) + close(series, i)) / 2
                            : (self.applyAsDouble(i - 1) + v(haClose, i - 1)) / 2);
                    Indicator<Num> haHigh = lambda(c, s, "high", (i, self) ->
                            Math.max(high(series, i), Math.max(v(haOpen, i), v(haClose, i))));
                    Indicator<Num> haLow = lambda(c, s, "low", (i, self) ->
                            Math.min(low(series, i), Math.min(v(haOpen, i), v(haClose, i))));
                    return Map.of("open", haOpen, "high", haHigh, "low", haLow, "close", haClose);
                });
    }
}
