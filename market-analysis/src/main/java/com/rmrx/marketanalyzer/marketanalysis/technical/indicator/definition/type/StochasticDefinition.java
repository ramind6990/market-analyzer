package com.rmrx.marketanalyzer.marketanalysis.technical.indicator.definition.type;

import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.block.FormulaBlock;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.block.PriceIndicator;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.block.Seed;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.block.SmaBlock;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.block.WindowExtremeBlock;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.definition.AbstractIndicatorDefinition;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.definition.BuildContext;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.definition.ComputationModel;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.definition.IndicatorFamily;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.definition.OutputDef;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.schema.ParamDef;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.schema.ParamSchema;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.spec.IndicatorSpec;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.spec.PriceField;
import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Slow stochastic oscillator: fastK = 100 * (C - LL(k)) / (HH(k) - LL(k)), k = SMA(fastK, kSmooth),
 * d = SMA(k, dPeriod). A flat window (HH == LL) gives 50. fastK is an internal output, so the windows over fastK and
 * k read stored values instead of candles.
 */
public final class StochasticDefinition extends AbstractIndicatorDefinition {

    public static final String TYPE = "STOCH";
    public static final String K_PERIOD = "kPeriod";
    public static final String K_SMOOTH = "kSmooth";
    public static final String D_PERIOD = "dPeriod";

    public static final String OUT_K = "k";
    public static final String OUT_D = "d";
    public static final String OUT_FAST_K = "fastK";

    public StochasticDefinition() {
        super(TYPE, "Stochastic oscillator (slow)", IndicatorFamily.MOMENTUM,
                Set.of(ComputationModel.WINDOW, ComputationModel.COMPOSITE),
                ParamSchema.of(
                        ParamDef.period(K_PERIOD, 14, "high/low window"),
                        ParamDef.period(K_SMOOTH, 3, "SMA period of %K"),
                        ParamDef.period(D_PERIOD, 3, "SMA period of %D")),
                List.of(
                        OutputDef.publicOutput(OUT_K, "%K 0..100 (smoothed)"),
                        OutputDef.publicOutput(OUT_D, "%D 0..100"),
                        OutputDef.internal(OUT_FAST_K, "raw %K")));
    }

    @Override
    public int lookback(IndicatorSpec spec) {
        return Math.max(spec.intParam(K_PERIOD), Math.max(spec.intParam(K_SMOOTH), spec.intParam(D_PERIOD))) - 1;
    }

    @Override
    public int warmup(IndicatorSpec spec) {
        return spec.intParam(K_PERIOD) + spec.intParam(K_SMOOTH) + spec.intParam(D_PERIOD) - 2;
    }

    @Override
    public Map<String, Indicator<Num>> build(BuildContext context, IndicatorSpec spec) {
        int kPeriod = spec.intParam(K_PERIOD);
        Indicator<Num> close = context.source(PriceField.CLOSE);
        Indicator<Num> highest = WindowExtremeBlock.highest(
                new PriceIndicator(context.series(), PriceField.HIGH), kPeriod, Seed.none());
        Indicator<Num> lowest = WindowExtremeBlock.lowest(
                new PriceIndicator(context.series(), PriceField.LOW), kPeriod, Seed.none());
        Indicator<Num> fastK = FormulaBlock.of(context.seed(spec, OUT_FAST_K), StochasticDefinition::fastK,
                close, highest, lowest);
        Indicator<Num> k = new SmaBlock(fastK, spec.intParam(K_SMOOTH), context.seed(spec, OUT_K));
        Indicator<Num> d = new SmaBlock(k, spec.intParam(D_PERIOD), context.seed(spec, OUT_D));
        return Map.of(OUT_K, k, OUT_D, d, OUT_FAST_K, fastK);
    }

    private static double fastK(double[] v) {
        double range = v[1] - v[2];
        return range == 0 ? 50 : 100 * (v[0] - v[2]) / range;
    }
}
