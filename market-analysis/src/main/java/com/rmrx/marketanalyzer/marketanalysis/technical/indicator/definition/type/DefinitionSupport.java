package com.rmrx.marketanalyzer.marketanalysis.technical.indicator.definition.type;

import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.block.DelegateBlock;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.block.FormulaBlock;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.block.LambdaBlock;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.block.Seed;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.definition.BuildContext;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.definition.OutputDef;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.schema.ParamDef;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.spec.IndicatorSpec;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.spec.OutputRef;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.spec.PriceField;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.spec.Source;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;

import java.util.Map;

/** Shared shortcuts for the definitions in this package. */
public final class DefinitionSupport {

    public static final String PERIOD = "period";
    public static final String SOURCE = "source";
    public static final String VALUE = OutputDef.VALUE;

    /** Bars per period after which an EMA (alpha 2/(n+1)) is considered converged. */
    public static final int EMA_FACTOR = 4;
    /** Bars per period after which a Wilder average (alpha 1/n) is considered converged. */
    public static final int WILDER_FACTOR = 8;

    private DefinitionSupport() {
    }

    public static ParamDef period(int defaultValue) {
        return ParamDef.period(PERIOD, defaultValue, "bars");
    }

    public static ParamDef source(PriceField defaultValue) {
        return ParamDef.source(SOURCE, defaultValue, "input series");
    }

    public static int period(IndicatorSpec spec) {
        return spec.intParam(PERIOD);
    }

    public static int param(IndicatorSpec spec, String name) {
        return spec.intParam(name);
    }

    public static double decimal(IndicatorSpec spec, String name) {
        return spec.decimalParam(name);
    }

    public static Indicator<Num> source(BuildContext context, IndicatorSpec spec) {
        return context.source(spec.sourceParam(SOURCE));
    }

    public static Indicator<Num> price(BuildContext context, PriceField field) {
        return context.source(field);
    }

    /** Output of another indicator ({@code value} output). */
    public static OutputRef ref(IndicatorSpec spec) {
        return spec.output(VALUE);
    }

    public static IndicatorSpec ema(int period, Source source) {
        return EmaDefinition.spec(period, source);
    }

    public static IndicatorSpec atr(int period) {
        return AtrDefinition.spec(period);
    }

    /** Seeded wrapper around a pure-window ta4j indicator. */
    public static Indicator<Num> wrap(BuildContext context, IndicatorSpec spec, String output, Indicator<?> ta4j) {
        return new DelegateBlock(ta4j, context.seed(spec, output));
    }

    public static Indicator<Num> lambda(BuildContext context, IndicatorSpec spec, String output,
                                 LambdaBlock.Computation computation) {
        return new LambdaBlock(context.series(), context.seed(spec, output), computation);
    }

    /** Helper series that is not an output: not stored, not seeded, must stay within the lookback. */
    public static Indicator<Num> helper(BuildContext context, LambdaBlock.Computation computation) {
        return new LambdaBlock(context.series(), Seed.none(), computation);
    }

    @SafeVarargs
    public static Indicator<Num> formula(BuildContext context, IndicatorSpec spec, String output,
                                  FormulaBlock.Formula formula, Indicator<Num>... inputs) {
        return FormulaBlock.of(context.seed(spec, output), formula, inputs);
    }

    public static Map<String, Indicator<Num>> single(Indicator<Num> value) {
        return Map.of(VALUE, value);
    }

    public static double v(Indicator<Num> indicator, int index) {
        Num num = indicator.getValue(index);
        return num.isNaN() ? Double.NaN : num.doubleValue();
    }

    public static double open(BarSeries series, int index) {
        return series.getBar(index).getOpenPrice().doubleValue();
    }

    public static double high(BarSeries series, int index) {
        return series.getBar(index).getHighPrice().doubleValue();
    }

    public static double low(BarSeries series, int index) {
        return series.getBar(index).getLowPrice().doubleValue();
    }

    public static double close(BarSeries series, int index) {
        return series.getBar(index).getClosePrice().doubleValue();
    }

    public static double volume(BarSeries series, int index) {
        return series.getBar(index).getVolume().doubleValue();
    }

    /** Close location value; 0 for a bar without range (ta4j would give NaN). */
    public static double clv(BarSeries series, int index) {
        double high = high(series, index);
        double low = low(series, index);
        double close = close(series, index);
        double range = high - low;
        return range == 0 ? 0 : (close - low - (high - close)) / range;
    }

    /** Mean of {@code input} over the last {@code period} bars (fewer at the series start). */
    public static double mean(Indicator<Num> input, int index, int period, int begin) {
        int start = Math.max(begin, index - period + 1);
        double sum = 0;
        for (int i = start; i <= index; i++) {
            sum += v(input, i);
        }
        return sum / (index - start + 1);
    }
}
