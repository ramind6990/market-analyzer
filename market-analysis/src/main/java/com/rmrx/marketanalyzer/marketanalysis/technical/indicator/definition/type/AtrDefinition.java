package com.rmrx.marketanalyzer.marketanalysis.technical.indicator.definition.type;

import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.block.ExponentialBlock;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.block.Seed;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.block.TrueRangeBlock;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.definition.AbstractIndicatorDefinition;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.definition.BuildContext;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.definition.ComputationModel;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.definition.IndicatorFamily;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.definition.OutputDef;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.schema.ParamDef;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.schema.ParamSchema;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.spec.IndicatorSpec;
import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Average true range: Wilder average (alpha 1/n) of the true range. */
public final class AtrDefinition extends AbstractIndicatorDefinition {

    public static final String TYPE = "ATR";
    public static final String PERIOD = "period";

    public AtrDefinition() {
        super(TYPE, "Average true range (Wilder)", IndicatorFamily.VOLATILITY, Set.of(ComputationModel.RECURSIVE),
                ParamSchema.of(ParamDef.period(PERIOD, 14, "smoothing period")),
                List.of(OutputDef.value("average true range")));
    }

    public static IndicatorSpec spec(int period) {
        return IndicatorSpec.of(TYPE).with(PERIOD, period);
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
        Indicator<Num> trueRange = new TrueRangeBlock(context.series(), Seed.none());
        return Map.of(OutputDef.VALUE,
                ExponentialBlock.wilder(trueRange, spec.intParam(PERIOD), context.seed(spec, OutputDef.VALUE)));
    }
}
