package com.rmrx.marketanalyzer.marketanalysis.technical.indicator.definition.type;

import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.block.ExponentialBlock;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.definition.AbstractIndicatorDefinition;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.definition.BuildContext;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.definition.ComputationModel;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.definition.IndicatorFamily;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.definition.OutputDef;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.schema.ParamDef;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.schema.ParamSchema;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.spec.IndicatorSpec;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.spec.PriceField;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.spec.Source;
import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Exponential moving average, alpha = 2/(n+1), started with the first input. Recursive: seeded by its last value. */
public final class EmaDefinition extends AbstractIndicatorDefinition {

    public static final String TYPE = "EMA";
    public static final String PERIOD = "period";
    public static final String SOURCE = "source";

    public EmaDefinition() {
        super(TYPE, "Exponential moving average", IndicatorFamily.MOVING_AVERAGE, Set.of(ComputationModel.RECURSIVE),
                ParamSchema.of(
                        ParamDef.period(PERIOD, 20, "smoothing period"),
                        ParamDef.source(SOURCE, PriceField.CLOSE, "input series")),
                List.of(OutputDef.value("average")));
    }

    public static IndicatorSpec spec(int period, Source source) {
        return IndicatorSpec.of(TYPE).with(PERIOD, period).with(SOURCE, source);
    }

    @Override
    public int lookback(IndicatorSpec spec) {
        return 0;
    }

    @Override
    public int warmup(IndicatorSpec spec) {
        return emaWarmup(spec.intParam(PERIOD));
    }

    @Override
    public Map<String, Indicator<Num>> build(BuildContext context, IndicatorSpec spec) {
        Indicator<Num> source = context.source(spec.sourceParam(SOURCE));
        return Map.of(OutputDef.VALUE,
                ExponentialBlock.ema(source, spec.intParam(PERIOD), context.seed(spec, OutputDef.VALUE)));
    }
}
