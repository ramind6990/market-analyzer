package com.rmrx.marketanalyzer.marketanalysis.technical.indicator.definition.type;

import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.block.ObvBlock;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.definition.AbstractIndicatorDefinition;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.definition.BuildContext;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.definition.ComputationModel;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.definition.IndicatorFamily;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.definition.OutputDef;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.schema.ParamSchema;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.spec.IndicatorSpec;
import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * On-balance volume. Cumulative: anchored at the first bar computed without a seed (0 there). Only slope and
 * divergence are meaningful; a recompute after a layout change moves the anchor.
 */
public final class ObvDefinition extends AbstractIndicatorDefinition {

    public static final String TYPE = "OBV";

    public ObvDefinition() {
        super(TYPE, "On-balance volume", IndicatorFamily.VOLUME, Set.of(ComputationModel.CUMULATIVE),
                ParamSchema.EMPTY, List.of(OutputDef.value("running volume total")));
    }

    @Override
    public int lookback(IndicatorSpec spec) {
        return 1;
    }

    @Override
    public int warmup(IndicatorSpec spec) {
        return 1;
    }

    @Override
    public Map<String, Indicator<Num>> build(BuildContext context, IndicatorSpec spec) {
        return Map.of(OutputDef.VALUE, new ObvBlock(context.series(), context.seed(spec, OutputDef.VALUE)));
    }
}
