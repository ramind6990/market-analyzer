package com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.type;

import com.rmrx.marketnexus.marketanalysis.technical.indicator.block.ChangeBlock;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.block.ExponentialBlock;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.block.FormulaBlock;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.block.Seed;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.AbstractIndicatorDefinition;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.BuildContext;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.ComputationModel;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.IndicatorFamily;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.OutputDef;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.schema.ParamDef;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.schema.ParamSchema;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.spec.IndicatorSpec;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.spec.PriceField;
import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Wilder RSI: 100 - 100 / (1 + avgGain / avgLoss), averages smoothed with alpha 1/n.
 * The two averages are internal outputs so they are stored and seeded.
 */
public final class RsiDefinition extends AbstractIndicatorDefinition {

    public static final String TYPE = "RSI";
    public static final String PERIOD = "period";
    public static final String SOURCE = "source";

    public static final String OUT_AVG_GAIN = "avgGain";
    public static final String OUT_AVG_LOSS = "avgLoss";

    public RsiDefinition() {
        super(TYPE, "Relative strength index (Wilder)", IndicatorFamily.MOMENTUM, Set.of(ComputationModel.RECURSIVE),
                ParamSchema.of(
                        ParamDef.period(PERIOD, 14, "smoothing period"),
                        ParamDef.source(SOURCE, PriceField.CLOSE, "input series")),
                List.of(
                        OutputDef.value("RSI 0..100"),
                        OutputDef.internal(OUT_AVG_GAIN, "Wilder average of gains"),
                        OutputDef.internal(OUT_AVG_LOSS, "Wilder average of losses")));
    }

    @Override
    public int lookback(IndicatorSpec spec) {
        return 1;
    }

    @Override
    public int warmup(IndicatorSpec spec) {
        return wilderWarmup(spec.intParam(PERIOD)) + 1;
    }

    @Override
    public Map<String, Indicator<Num>> build(BuildContext context, IndicatorSpec spec) {
        int period = spec.intParam(PERIOD);
        Indicator<Num> source = context.source(spec.sourceParam(SOURCE));
        Indicator<Num> avgGain = ExponentialBlock.wilder(ChangeBlock.gain(source, Seed.none()), period,
                context.seed(spec, OUT_AVG_GAIN));
        Indicator<Num> avgLoss = ExponentialBlock.wilder(ChangeBlock.loss(source, Seed.none()), period,
                context.seed(spec, OUT_AVG_LOSS));
        Indicator<Num> rsi = FormulaBlock.of(context.seed(spec, OutputDef.VALUE), RsiDefinition::rsi, avgGain, avgLoss);
        return Map.of(OutputDef.VALUE, rsi, OUT_AVG_GAIN, avgGain, OUT_AVG_LOSS, avgLoss);
    }

    private static double rsi(double[] v) {
        double gain = v[0];
        double loss = v[1];
        if (loss == 0) {
            return gain == 0 ? 0 : 100;
        }
        return 100 - 100 / (1 + gain / loss);
    }
}
