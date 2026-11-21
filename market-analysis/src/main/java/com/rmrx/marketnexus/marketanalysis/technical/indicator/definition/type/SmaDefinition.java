package com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.type;

import com.rmrx.marketnexus.marketanalysis.technical.indicator.block.SmaBlock;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.AbstractIndicatorDefinition;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.BuildContext;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.ComputationModel;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.IndicatorFamily;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.OutputDef;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.schema.ParamDef;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.schema.ParamSchema;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.spec.IndicatorSpec;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.spec.PriceField;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.spec.Source;
import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Simple moving average. Window: exact once {@code period} bars exist, needs no seed. */
public final class SmaDefinition extends AbstractIndicatorDefinition {

    public static final String TYPE = "SMA";
    public static final String PERIOD = "period";
    public static final String SOURCE = "source";

    public SmaDefinition() {
        super(TYPE, "Simple moving average", IndicatorFamily.MOVING_AVERAGE, Set.of(ComputationModel.WINDOW),
                ParamSchema.of(
                        ParamDef.period(PERIOD, 20, "bars averaged"),
                        ParamDef.source(SOURCE, PriceField.CLOSE, "input series")),
                List.of(OutputDef.value("average")));
    }

    public static IndicatorSpec spec(int period, Source source) {
        return IndicatorSpec.of(TYPE).with(PERIOD, period).with(SOURCE, source);
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
        Indicator<Num> source = context.source(spec.sourceParam(SOURCE));
        return Map.of(OutputDef.VALUE,
                new SmaBlock(source, spec.intParam(PERIOD), context.seed(spec, OutputDef.VALUE)));
    }
}
