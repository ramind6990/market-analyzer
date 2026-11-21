package com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.type;

import com.rmrx.marketnexus.marketanalysis.technical.indicator.block.DirectionalMoveBlock;
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
import com.rmrx.marketnexus.marketanalysis.technical.indicator.spec.OutputRef;
import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * ADX / DMI (Wilder). +DI = 100 * avg(+DM) / ATR, -DI likewise, DX = 100 * |+DI - -DI| / (+DI + -DI),
 * ADX = Wilder average of DX. ATR is a shared dependency; the DM averages are internal outputs (seeded).
 */
public final class AdxDefinition extends AbstractIndicatorDefinition {

    public static final String TYPE = "ADX";
    public static final String DI_LENGTH = "diLength";
    public static final String ADX_SMOOTHING = "adxSmoothing";

    public static final String OUT_PLUS_DI = "plusDi";
    public static final String OUT_MINUS_DI = "minusDi";
    public static final String OUT_ADX = "adx";
    public static final String OUT_PLUS_DM_AVG = "plusDmAvg";
    public static final String OUT_MINUS_DM_AVG = "minusDmAvg";
    public static final String OUT_DX = "dx";

    public AdxDefinition() {
        super(TYPE, "Average directional index with +DI/-DI", IndicatorFamily.TREND,
                Set.of(ComputationModel.RECURSIVE, ComputationModel.COMPOSITE),
                ParamSchema.of(
                        ParamDef.period(DI_LENGTH, 14, "period of the DM and ATR averages"),
                        ParamDef.period(ADX_SMOOTHING, 14, "period of the DX average")),
                List.of(
                        OutputDef.publicOutput(OUT_PLUS_DI, "+DI 0..100"),
                        OutputDef.publicOutput(OUT_MINUS_DI, "-DI 0..100"),
                        OutputDef.publicOutput(OUT_ADX, "ADX 0..100"),
                        OutputDef.internal(OUT_PLUS_DM_AVG, "Wilder average of +DM"),
                        OutputDef.internal(OUT_MINUS_DM_AVG, "Wilder average of -DM"),
                        OutputDef.internal(OUT_DX, "directional index")));
    }

    @Override
    public List<OutputRef> dependencies(IndicatorSpec spec) {
        return List.of(atr(spec));
    }

    @Override
    public int lookback(IndicatorSpec spec) {
        return 1;
    }

    /** DM averages converge like the ATR dependency (counted there); the DX average comes on top. */
    @Override
    public int warmup(IndicatorSpec spec) {
        return wilderWarmup(spec.intParam(ADX_SMOOTHING));
    }

    @Override
    public Map<String, Indicator<Num>> build(BuildContext context, IndicatorSpec spec) {
        int diLength = spec.intParam(DI_LENGTH);
        Indicator<Num> atr = context.source(atr(spec));
        Indicator<Num> plusDmAvg = ExponentialBlock.wilder(DirectionalMoveBlock.plus(context.series(), Seed.none()),
                diLength, context.seed(spec, OUT_PLUS_DM_AVG));
        Indicator<Num> minusDmAvg = ExponentialBlock.wilder(DirectionalMoveBlock.minus(context.series(), Seed.none()),
                diLength, context.seed(spec, OUT_MINUS_DM_AVG));
        Indicator<Num> plusDi = FormulaBlock.of(context.seed(spec, OUT_PLUS_DI), AdxDefinition::di, plusDmAvg, atr);
        Indicator<Num> minusDi = FormulaBlock.of(context.seed(spec, OUT_MINUS_DI), AdxDefinition::di, minusDmAvg, atr);
        Indicator<Num> dx = FormulaBlock.of(context.seed(spec, OUT_DX), AdxDefinition::dx, plusDi, minusDi);
        Indicator<Num> adx = ExponentialBlock.wilder(dx, spec.intParam(ADX_SMOOTHING), context.seed(spec, OUT_ADX));
        return Map.of(OUT_PLUS_DI, plusDi, OUT_MINUS_DI, minusDi, OUT_ADX, adx,
                OUT_PLUS_DM_AVG, plusDmAvg, OUT_MINUS_DM_AVG, minusDmAvg, OUT_DX, dx);
    }

    private static OutputRef atr(IndicatorSpec spec) {
        return AtrDefinition.spec(spec.intParam(DI_LENGTH)).output(OutputDef.VALUE);
    }

    private static double di(double[] v) {
        return v[1] == 0 ? 0 : 100 * v[0] / v[1];
    }

    private static double dx(double[] v) {
        double sum = v[0] + v[1];
        return sum == 0 ? 0 : 100 * Math.abs(v[0] - v[1]) / sum;
    }
}
