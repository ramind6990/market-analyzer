package com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.type;

import com.rmrx.marketnexus.marketanalysis.technical.indicator.block.ExponentialBlock;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.block.FormulaBlock;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.AbstractIndicatorDefinition;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.BuildContext;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.ComputationModel;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.IndicatorFamily;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.OutputDef;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.schema.IndicatorSpecException;
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

/**
 * MACD: line = EMA(fast) - EMA(slow), signal = EMA(line, signal), histogram = line - signal.
 * The two EMAs are shared dependencies; the signal EMA is seeded through the {@code signal} output itself.
 */
public final class MacdDefinition extends AbstractIndicatorDefinition {

    public static final String TYPE = "MACD";
    public static final String FAST = "fast";
    public static final String SLOW = "slow";
    public static final String SIGNAL = "signal";
    public static final String SOURCE = "source";

    public static final String OUT_LINE = "line";
    public static final String OUT_SIGNAL = "signal";
    public static final String OUT_HISTOGRAM = "histogram";

    public MacdDefinition() {
        super(TYPE, "Moving average convergence divergence", IndicatorFamily.TREND,
                Set.of(ComputationModel.RECURSIVE, ComputationModel.COMPOSITE),
                ParamSchema.of(
                        ParamDef.period(FAST, 12, "fast EMA period"),
                        ParamDef.period(SLOW, 26, "slow EMA period"),
                        ParamDef.period(SIGNAL, 9, "signal EMA period"),
                        ParamDef.source(SOURCE, PriceField.CLOSE, "input series")),
                List.of(
                        OutputDef.publicOutput(OUT_LINE, "fast EMA - slow EMA"),
                        OutputDef.publicOutput(OUT_SIGNAL, "EMA of the line"),
                        OutputDef.publicOutput(OUT_HISTOGRAM, "line - signal")));
    }

    @Override
    public IndicatorSpec canonicalize(IndicatorSpec spec) {
        IndicatorSpec canonical = super.canonicalize(spec);
        if (canonical.intParam(FAST) >= canonical.intParam(SLOW)) {
            throw new IndicatorSpecException(TYPE + ": fast must be smaller than slow: " + canonical);
        }
        return canonical;
    }

    @Override
    public List<OutputRef> dependencies(IndicatorSpec spec) {
        return List.of(fastEma(spec), slowEma(spec));
    }

    @Override
    public int lookback(IndicatorSpec spec) {
        return 0;
    }

    @Override
    public int warmup(IndicatorSpec spec) {
        return emaWarmup(spec.intParam(SIGNAL));
    }

    @Override
    public Map<String, Indicator<Num>> build(BuildContext context, IndicatorSpec spec) {
        Indicator<Num> fast = context.source(fastEma(spec));
        Indicator<Num> slow = context.source(slowEma(spec));
        Indicator<Num> line = FormulaBlock.of(context.seed(spec, OUT_LINE), v -> v[0] - v[1], fast, slow);
        Indicator<Num> signal = ExponentialBlock.ema(line, spec.intParam(SIGNAL), context.seed(spec, OUT_SIGNAL));
        Indicator<Num> histogram = FormulaBlock.of(context.seed(spec, OUT_HISTOGRAM), v -> v[0] - v[1], line, signal);
        return Map.of(OUT_LINE, line, OUT_SIGNAL, signal, OUT_HISTOGRAM, histogram);
    }

    private static OutputRef fastEma(IndicatorSpec spec) {
        return EmaDefinition.spec(spec.intParam(FAST), spec.sourceParam(SOURCE)).output(OutputDef.VALUE);
    }

    private static OutputRef slowEma(IndicatorSpec spec) {
        return EmaDefinition.spec(spec.intParam(SLOW), spec.sourceParam(SOURCE)).output(OutputDef.VALUE);
    }
}
