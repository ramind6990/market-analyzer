package com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.type;

import com.rmrx.marketnexus.marketanalysis.technical.indicator.block.FormulaBlock;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.block.Seed;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.block.StdDevBlock;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.AbstractIndicatorDefinition;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.BuildContext;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.ComputationModel;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.IndicatorFamily;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.OutputDef;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.schema.ParamDef;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.schema.ParamSchema;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.spec.IndicatorSpec;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.spec.OutputRef;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.spec.PriceField;
import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Bollinger Bands: middle = SMA (shared dependency), upper/lower = middle +/- multiplier * population stddev. */
public final class BollingerDefinition extends AbstractIndicatorDefinition {

    public static final String TYPE = "BBANDS";
    public static final String PERIOD = "period";
    public static final String MULTIPLIER = "multiplier";
    public static final String SOURCE = "source";

    public static final String OUT_UPPER = "upper";
    public static final String OUT_MIDDLE = "middle";
    public static final String OUT_LOWER = "lower";
    public static final String OUT_PERCENT_B = "percentB";
    public static final String OUT_BANDWIDTH = "bandwidth";

    public BollingerDefinition() {
        super(TYPE, "Bollinger Bands", IndicatorFamily.VOLATILITY,
                Set.of(ComputationModel.WINDOW, ComputationModel.COMPOSITE),
                ParamSchema.of(
                        ParamDef.period(PERIOD, 20, "SMA and stddev window"),
                        ParamDef.decimal(MULTIPLIER, 2.0, 0.1, 10, "band width in standard deviations"),
                        ParamDef.source(SOURCE, PriceField.CLOSE, "input series")),
                List.of(
                        OutputDef.publicOutput(OUT_UPPER, "upper band"),
                        OutputDef.publicOutput(OUT_MIDDLE, "middle band (SMA)"),
                        OutputDef.publicOutput(OUT_LOWER, "lower band"),
                        OutputDef.publicOutput(OUT_PERCENT_B, "(source - lower) / (upper - lower)"),
                        OutputDef.publicOutput(OUT_BANDWIDTH, "100 * (upper - lower) / middle")));
    }

    @Override
    public List<OutputRef> dependencies(IndicatorSpec spec) {
        return List.of(sma(spec));
    }

    @Override
    public int lookback(IndicatorSpec spec) {
        return spec.intParam(PERIOD) - 1;
    }

    @Override
    public int warmup(IndicatorSpec spec) {
        return spec.intParam(PERIOD);
    }

    @Override
    public Map<String, Indicator<Num>> build(BuildContext context, IndicatorSpec spec) {
        double multiplier = spec.decimalParam(MULTIPLIER);
        Indicator<Num> sma = context.source(sma(spec));
        Indicator<Num> stdDev = new StdDevBlock(context.source(spec.sourceParam(SOURCE)), spec.intParam(PERIOD),
                Seed.none());
        Indicator<Num> middle = FormulaBlock.of(context.seed(spec, OUT_MIDDLE), v -> v[0], sma);
        Indicator<Num> upper = FormulaBlock.of(context.seed(spec, OUT_UPPER), v -> v[0] + multiplier * v[1],
                sma, stdDev);
        Indicator<Num> lower = FormulaBlock.of(context.seed(spec, OUT_LOWER), v -> v[0] - multiplier * v[1],
                sma, stdDev);
        Indicator<Num> percentB = FormulaBlock.of(context.seed(spec, OUT_PERCENT_B),
                v -> (v[0] - v[2]) / (v[1] - v[2]), context.source(spec.sourceParam(SOURCE)), upper, lower);
        Indicator<Num> bandwidth = FormulaBlock.of(context.seed(spec, OUT_BANDWIDTH),
                v -> (v[0] - v[1]) / v[2] * 100, upper, lower, middle);
        return Map.of(OUT_UPPER, upper, OUT_MIDDLE, middle, OUT_LOWER, lower,
                OUT_PERCENT_B, percentB, OUT_BANDWIDTH, bandwidth);
    }

    private static OutputRef sma(IndicatorSpec spec) {
        return SmaDefinition.spec(spec.intParam(PERIOD), spec.sourceParam(SOURCE)).output(OutputDef.VALUE);
    }
}
