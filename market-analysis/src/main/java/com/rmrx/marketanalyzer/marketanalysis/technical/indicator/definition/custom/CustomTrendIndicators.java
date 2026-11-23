package com.rmrx.marketanalyzer.marketanalysis.technical.indicator.definition.custom;

import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.block.ExponentialBlock;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.block.FormulaBlock;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.block.Seed;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.block.WindowExtremeBlock;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.definition.BuildContext;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.definition.IndicatorDefinition;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.definition.OutputDef;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.schema.ParamDef;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.spec.IndicatorSpec;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.spec.PriceField;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;

import java.util.List;
import java.util.Map;

import static com.rmrx.marketanalyzer.marketanalysis.technical.indicator.definition.ComputationModel.COMPOSITE;
import static com.rmrx.marketanalyzer.marketanalysis.technical.indicator.definition.ComputationModel.RECURSIVE;
import static com.rmrx.marketanalyzer.marketanalysis.technical.indicator.definition.ComputationModel.STATELESS;
import static com.rmrx.marketanalyzer.marketanalysis.technical.indicator.definition.ComputationModel.WINDOW;
import static com.rmrx.marketanalyzer.marketanalysis.technical.indicator.definition.DefinitionBuilder.define;
import static com.rmrx.marketanalyzer.marketanalysis.technical.indicator.definition.IndicatorFamily.TREND;
import static com.rmrx.marketanalyzer.marketanalysis.technical.indicator.definition.type.DefinitionSupport.*;

/** Trend indicators without a ta4j indicator. */
public final class CustomTrendIndicators {

    public static final String ELDER_RAY = "ELDER_RAY";
    public static final String TRIX = "TRIX";
    public static final String STC = "STC";
    public static final String VORTEX = "VORTEX";
    public static final String PSAR = "PSAR";

    private CustomTrendIndicators() {
    }

    public static List<IndicatorDefinition> definitions() {
        return List.of(elderRay(), trix(), schaffTrendCycle(), vortex(), parabolicSar());
    }

    /**
     * Parabolic SAR, Wilder's algorithm as in TA-Lib {@code SAR} (not ta4j's variant, whose ta4j implementation is
     * pathologically slow). Start on the second bar: long unless the minus DM of the first two bars is positive; long
     * starts with SAR = first low, EP = second high (short mirrored), AF = acceleration. Per bar: on a reversal the bar's
     * SAR is the old EP (limited by the previous and current extreme), AF resets and EP becomes the current extreme;
     * otherwise a new extreme moves EP and raises AF by acceleration up to maximum. The next SAR = SAR + AF (EP - SAR),
     * kept outside the previous and current bar's range. State after each bar (next SAR, EP, AF, trend) is stored as
     * INTERNAL outputs, so a seeded job continues with O(1) work per bar. The first bar's value is NaN.
     */
    static IndicatorDefinition parabolicSar() {
        return define(PSAR).description("Parabolic SAR (Wilder / TA-Lib)").family(TREND).models(RECURSIVE)
                .params(ParamDef.decimal("acceleration", 0.02, 0.001, 1, "AF start and step"),
                        ParamDef.decimal("maximum", 0.2, 0.001, 1, "AF maximum"))
                .outputs(OutputDef.value("stop and reverse level"),
                        OutputDef.internal("nextSar", "SAR for the next bar"),
                        OutputDef.internal("ep", "extreme point"),
                        OutputDef.internal("af", "acceleration factor"),
                        OutputDef.internal("trend", "+1 long, -1 short"))
                .validate(s -> decimal(s, "acceleration") <= decimal(s, "maximum"), "acceleration must not exceed maximum")
                .lookback(s -> 2).warmup(s -> 100)
                .build((c, s) -> {
                    SarStep step = new SarStep(c.series(), decimal(s, "acceleration"), decimal(s, "maximum"));
                    step.nextSar = lambda(c, s, "nextSar", (i, self) -> step.at(i).nextSar());
                    step.ep = lambda(c, s, "ep", (i, self) -> step.at(i).ep());
                    step.af = lambda(c, s, "af", (i, self) -> step.at(i).af());
                    step.trend = lambda(c, s, "trend", (i, self) -> step.at(i).trend());
                    Indicator<Num> value = lambda(c, s, VALUE, (i, self) -> step.at(i).value());
                    return Map.of(VALUE, value, "nextSar", step.nextSar, "ep", step.ep, "af", step.af,
                            "trend", step.trend);
                });
    }

    /** SAR of a bar and the state after it. */
    private record SarState(double value, double nextSar, double ep, double af, double trend) {
    }

    /** One TA-Lib SAR iteration; reads the state of the previous bar from the (seeded) state outputs. */
    private static final class SarStep {
        private final BarSeries series;
        private final int begin;
        private final double acceleration;
        private final double maximum;
        Indicator<Num> nextSar;
        Indicator<Num> ep;
        Indicator<Num> af;
        Indicator<Num> trend;

        SarStep(BarSeries series, double acceleration, double maximum) {
            this.series = series;
            this.begin = series.getBeginIndex();
            this.acceleration = acceleration;
            this.maximum = maximum;
        }

        SarState at(int i) {
            if (i <= begin) {
                return new SarState(Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN);
            }
            double newHigh = high(series, i);
            double newLow = low(series, i);
            boolean isLong;
            double sar;
            double extreme;
            double factor;
            double prevHigh;
            double prevLow;
            if (i == begin + 1) {
                // TA-Lib start: direction from the minus DM of the first two bars; in the first iteration the
                // previous extremes are those of the current bar
                double diffMinus = low(series, begin) - newLow;
                double diffPlus = newHigh - high(series, begin);
                boolean minusDm = diffMinus > 0 && diffPlus < diffMinus;
                isLong = !minusDm;
                sar = isLong ? low(series, begin) : high(series, begin);
                extreme = isLong ? newHigh : newLow;
                factor = acceleration;
                prevHigh = newHigh;
                prevLow = newLow;
            } else {
                isLong = v(trend, i - 1) > 0;
                sar = v(nextSar, i - 1);
                extreme = v(ep, i - 1);
                factor = v(af, i - 1);
                prevHigh = high(series, i - 1);
                prevLow = low(series, i - 1);
            }
            double value;
            if (isLong) {
                if (newLow <= sar) {
                    isLong = false;
                    sar = Math.max(extreme, Math.max(prevHigh, newHigh));
                    value = sar;
                    factor = acceleration;
                    extreme = newLow;
                    sar = Math.max(sar + factor * (extreme - sar), Math.max(prevHigh, newHigh));
                } else {
                    value = sar;
                    if (newHigh > extreme) {
                        extreme = newHigh;
                        factor = Math.min(factor + acceleration, maximum);
                    }
                    sar = Math.min(sar + factor * (extreme - sar), Math.min(prevLow, newLow));
                }
            } else if (newHigh >= sar) {
                isLong = true;
                sar = Math.min(extreme, Math.min(prevLow, newLow));
                value = sar;
                factor = acceleration;
                extreme = newHigh;
                sar = Math.min(sar + factor * (extreme - sar), Math.min(prevLow, newLow));
            } else {
                value = sar;
                if (newLow < extreme) {
                    extreme = newLow;
                    factor = Math.min(factor + acceleration, maximum);
                }
                sar = Math.max(sar + factor * (extreme - sar), Math.max(prevHigh, newHigh));
            }
            return new SarState(value, sar, extreme, factor, isLong ? 1 : -1);
        }
    }

    /**
     * Vortex indicator (Botes/Siepman; pandas-ta {@code vortex}): +VI = sum |high - previous low| / sum TR,
     * -VI = sum |low - previous high| / sum TR over n bars. 0 when the TR sum is 0, NaN until n + 1 bars exist.
     */
    static IndicatorDefinition vortex() {
        return define(VORTEX).description("Vortex indicator").family(TREND).models(WINDOW)
                .params(period(14))
                .outputs(OutputDef.publicOutput("plus", "+VI"), OutputDef.publicOutput("minus", "-VI"))
                .lookback(s -> period(s)).warmup(s -> period(s) + 1)
                .build((c, s) -> {
                    BarSeries series = c.series();
                    int begin = series.getBeginIndex();
                    int n = period(s);
                    return Map.of(
                            "plus", lambda(c, s, "plus", (i, self) -> vortexValue(series, begin, i, n, true)),
                            "minus", lambda(c, s, "minus", (i, self) -> vortexValue(series, begin, i, n, false)));
                });
    }

    private static double vortexValue(BarSeries series, int begin, int i, int n, boolean plus) {
        if (i - n < begin) {
            return Double.NaN;
        }
        double movement = 0;
        double trueRange = 0;
        for (int j = i - n + 1; j <= i; j++) {
            double previousClose = close(series, j - 1);
            movement += plus ? Math.abs(high(series, j) - low(series, j - 1))
                    : Math.abs(low(series, j) - high(series, j - 1));
            trueRange += Math.max(high(series, j), previousClose) - Math.min(low(series, j), previousClose);
        }
        return trueRange == 0 ? 0 : movement / trueRange;
    }

    /** Elder ray (pandas-ta {@code eri}): bull power = high - EMA(close), bear power = low - EMA(close). */
    static IndicatorDefinition elderRay() {
        return define(ELDER_RAY).description("Elder ray bull/bear power").family(TREND).models(STATELESS, COMPOSITE)
                .params(period(13))
                .outputs(OutputDef.publicOutput("bull", "high - EMA"), OutputDef.publicOutput("bear", "low - EMA"))
                .dependencies(s -> List.of(ref(closeEma(s))))
                .lookback(s -> 0).warmup(s -> 0)
                .build((c, s) -> {
                    Indicator<Num> ema = c.source(ref(closeEma(s)));
                    return Map.of(
                            "bull", formula(c, s, "bull", x -> x[0] - x[1], price(c, PriceField.HIGH), ema),
                            "bear", formula(c, s, "bear", x -> x[0] - x[1], price(c, PriceField.LOW), ema));
                });
    }

    private static IndicatorSpec closeEma(IndicatorSpec spec) {
        return ema(period(spec), PriceField.CLOSE);
    }

    /**
     * TRIX (TA-Lib {@code TRIX}, pandas-ta {@code trix}): 100 * one-bar rate of change of the triple-smoothed EMA
     * (three chained EMA nodes, shared with TEMA of the same period), plus an EMA signal line. 0 on the first bar.
     */
    static IndicatorDefinition trix() {
        return define(TRIX).description("Triple exponential average rate of change").family(TREND)
                .models(RECURSIVE, COMPOSITE)
                .params(period(15), ParamDef.period("signal", 9, "EMA period of the signal line"),
                        source(PriceField.CLOSE))
                .outputs(OutputDef.value("100 * ROC(1) of EMA(EMA(EMA))"),
                        OutputDef.publicOutput("signal", "EMA of the value"))
                .dependencies(s -> List.of(ref(tripleEma(s))))
                .lookback(s -> 1).warmup(s -> EMA_FACTOR * param(s, "signal") + 1)
                .build((c, s) -> {
                    Indicator<Num> triple = c.source(ref(tripleEma(s)));
                    int begin = c.series().getBeginIndex();
                    Indicator<Num> value = lambda(c, s, VALUE, (i, self) -> {
                        if (i <= begin) {
                            return 0;
                        }
                        double previous = v(triple, i - 1);
                        return previous == 0 ? 0 : 100 * (v(triple, i) - previous) / previous;
                    });
                    return Map.of(VALUE, value,
                            "signal", ExponentialBlock.ema(value, param(s, "signal"), c.seed(s, "signal")));
                });
    }

    private static IndicatorSpec tripleEma(IndicatorSpec spec) {
        IndicatorSpec e1 = ema(period(spec), spec.sourceParam(SOURCE));
        IndicatorSpec e2 = ema(period(spec), ref(e1));
        return ema(period(spec), ref(e2));
    }

    /**
     * Schaff trend cycle (Schaff's original / TradingView, pandas-ta {@code stc}): MACD = EMA(fast) - EMA(slow)
     * (shared EMA nodes); stoch1 = stochastic of MACD over {@code cycle} bars (previous value when the window is flat);
     * smooth1 = exponential smoothing of stoch1 with {@code factor}; stoch2 = stochastic of smooth1; value = smoothing
     * of stoch2. stoch1, smooth1, stoch2 are recursive state (INTERNAL outputs).
     */
    static IndicatorDefinition schaffTrendCycle() {
        return define(STC).description("Schaff trend cycle").family(TREND).models(RECURSIVE, WINDOW, COMPOSITE)
                .params(ParamDef.period("fast", 23, "fast EMA period"), ParamDef.period("slow", 50, "slow EMA period"),
                        ParamDef.period("cycle", 10, "stochastic window"),
                        ParamDef.decimal("factor", 0.5, 0.01, 1, "smoothing factor"), source(PriceField.CLOSE))
                .outputs(OutputDef.value("STC 0..100"),
                        OutputDef.internal("stoch1", "stochastic of MACD"),
                        OutputDef.internal("smooth1", "smoothed stoch1"),
                        OutputDef.internal("stoch2", "stochastic of smooth1"))
                .validate(s -> param(s, "fast") < param(s, "slow"), "fast must be smaller than slow")
                .dependencies(s -> List.of(ref(stcEma(s, "fast")), ref(stcEma(s, "slow"))))
                .lookback(s -> Math.max(1, param(s, "cycle") - 1)).warmup(s -> 2 * param(s, "cycle") + 20)
                .build((c, s) -> {
                    int begin = c.series().getBeginIndex();
                    int cycle = param(s, "cycle");
                    double factor = decimal(s, "factor");
                    Indicator<Num> macd = FormulaBlock.of(Seed.none(), x -> x[0] - x[1],
                            c.source(ref(stcEma(s, "fast"))), c.source(ref(stcEma(s, "slow"))));
                    Indicator<Num> stoch1 = stochastic(c, s, "stoch1", macd, cycle, begin);
                    Indicator<Num> smooth1 = smoothing(c, s, "smooth1", stoch1, factor, begin);
                    Indicator<Num> stoch2 = stochastic(c, s, "stoch2", smooth1, cycle, begin);
                    Indicator<Num> value = smoothing(c, s, VALUE, stoch2, factor, begin);
                    return Map.of(VALUE, value, "stoch1", stoch1, "smooth1", smooth1, "stoch2", stoch2);
                });
    }

    private static IndicatorSpec stcEma(IndicatorSpec spec, String period) {
        return ema(param(spec, period), spec.sourceParam(SOURCE));
    }

    /** 100 * (x - min) / (max - min) over the window; previous value (0 at the start) when the window is flat. */
    private static Indicator<Num> stochastic(BuildContext c,
                                             IndicatorSpec s, String output, Indicator<Num> input, int window,
                                             int begin) {
        Indicator<Num> max = WindowExtremeBlock.highest(input, window, Seed.none());
        Indicator<Num> min = WindowExtremeBlock.lowest(input, window, Seed.none());
        return lambda(c, s, output, (i, self) -> {
            double range = v(max, i) - v(min, i);
            if (range > 0) {
                return 100 * (v(input, i) - v(min, i)) / range;
            }
            return i <= begin ? 0 : self.applyAsDouble(i - 1);
        });
    }

    /** previous + factor * (x - previous), started with the first input. */
    private static Indicator<Num> smoothing(BuildContext c,
                                            IndicatorSpec s, String output, Indicator<Num> input, double factor,
                                            int begin) {
        return lambda(c, s, output, (i, self) -> {
            if (i <= begin) {
                return v(input, i);
            }
            double previous = self.applyAsDouble(i - 1);
            return previous + factor * (v(input, i) - previous);
        });
    }
}
