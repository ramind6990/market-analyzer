package com.rmrx.marketanalyzer.marketanalysis.technical.indicator.definition.type;

import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.block.ExponentialBlock;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.definition.IndicatorDefinition;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.definition.IndicatorFamily;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.definition.OutputDef;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.schema.ParamDef;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.spec.IndicatorSpec;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.spec.PriceField;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.DPOIndicator;
import org.ta4j.core.indicators.aroon.AroonDownIndicator;
import org.ta4j.core.indicators.aroon.AroonOscillatorIndicator;
import org.ta4j.core.indicators.aroon.AroonUpIndicator;
import org.ta4j.core.indicators.ichimoku.IchimokuKijunSenIndicator;
import org.ta4j.core.indicators.ichimoku.IchimokuSenkouSpanAIndicator;
import org.ta4j.core.indicators.ichimoku.IchimokuSenkouSpanBIndicator;
import org.ta4j.core.indicators.ichimoku.IchimokuTenkanSenIndicator;
import org.ta4j.core.indicators.statistics.SimpleLinearRegressionIndicator;
import org.ta4j.core.indicators.statistics.SimpleLinearRegressionIndicator.SimpleLinearRegressionType;
import org.ta4j.core.num.Num;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static com.rmrx.marketanalyzer.marketanalysis.technical.indicator.definition.ComputationModel.COMPOSITE;
import static com.rmrx.marketanalyzer.marketanalysis.technical.indicator.definition.ComputationModel.RECURSIVE;
import static com.rmrx.marketanalyzer.marketanalysis.technical.indicator.definition.ComputationModel.WINDOW;
import static com.rmrx.marketanalyzer.marketanalysis.technical.indicator.definition.DefinitionBuilder.define;
import static com.rmrx.marketanalyzer.marketanalysis.technical.indicator.definition.IndicatorFamily.TREND;
import static com.rmrx.marketanalyzer.marketanalysis.technical.indicator.definition.type.DefinitionSupport.*;

/** Trend indicators besides MACD and ADX (catalog section 3.3). Formulas follow ta4j 0.18. */
public final class TrendIndicators {

    public static final String PPO = "PPO";
    public static final String AROON = "AROON";
    public static final String SUPERTREND = "SUPERTREND";
    public static final String ICHIMOKU = "ICHIMOKU";
    public static final String DPO = "DPO";
    public static final String MASS_INDEX = "MASS_INDEX";
    public static final String LINREG = "LINREG";
    public static final String RWI = "RWI";

    private TrendIndicators() {
    }

    public static List<IndicatorDefinition> definitions() {
        return List.of(ppo(), aroon(), supertrend(), ichimoku(), dpo(), massIndex(), linearRegression(), randomWalkIndex());
    }

    /**
     * Random walk index (ta4j {@code RWIHighIndicator} / {@code RWILowIndicator}, same formula): high = max(0, max over
     * n = 2..period of (high - low n-1 bars ago) / (ATR(n) * sqrt(n))), low = max(0, max over n of (high n-1 bars ago -
     * low) / (ATR(n) * sqrt(n))); NaN until period bars exist. ta4j creates a new ATR per n on every call (each
     * recomputed from the series start); here ATR(2..period) are shared, seeded ATR nodes, so a bar costs O(period).
     * An n with ATR 0 is skipped.
     */
    static IndicatorDefinition randomWalkIndex() {
        return define(RWI).description("Random walk index").family(TREND).models(WINDOW, COMPOSITE)
                .params(ParamDef.integer(PERIOD, 14, 2, 200, "longest distance n")).outputs(
                        OutputDef.publicOutput("high", "RWI high"), OutputDef.publicOutput("low", "RWI low"))
                .dependencies(s -> IntStream.rangeClosed(2, period(s))
                        .mapToObj(n -> ref(atr(n))).toList())
                .lookback(s -> period(s) - 1).warmup(s -> period(s))
                .build((c, s) -> {
                    BarSeries series = c.series();
                    int begin = series.getBeginIndex();
                    int period = period(s);
                    List<Indicator<Num>> atrs = IntStream.rangeClosed(2, period)
                            .mapToObj(n -> c.source(ref(atr(n)))).toList();
                    return Map.of(
                            "high", lambda(c, s, "high", (i, self) -> rwi(series, atrs, begin, i, period, true)),
                            "low", lambda(c, s, "low", (i, self) -> rwi(series, atrs, begin, i, period, false)));
                });
    }

    private static double rwi(BarSeries series, List<Indicator<Num>> atrs, int begin, int i, int period, boolean high) {
        if (i - period + 1 < begin) {
            return Double.NaN;
        }
        double max = 0;
        for (int n = 2; n <= period; n++) {
            double atr = v(atrs.get(n - 2), i);
            if (atr == 0) {
                continue;
            }
            int back = i - n + 1;
            double distance = high ? high(series, i) - low(series, back) : high(series, back) - low(series, i);
            max = Math.max(max, distance / (atr * Math.sqrt(n)));
        }
        return max;
    }

    /** Percentage price oscillator on any source: line = 100 * (EMA fast - EMA slow) / EMA slow. */
    static IndicatorDefinition ppo() {
        return percentageOscillator(PPO, "Percentage price oscillator", TREND, PriceField.CLOSE);
    }

    /** Shared by PPO and PVO (volume). */
    static IndicatorDefinition percentageOscillator(String type, String description, IndicatorFamily family,
                                                    PriceField defaultSource) {
        return define(type).description(description).family(family).models(RECURSIVE, COMPOSITE)
                .params(ParamDef.period("fast", 12, "fast EMA period"), ParamDef.period("slow", 26, "slow EMA period"),
                        ParamDef.period("signal", 9, "signal EMA period"), source(defaultSource))
                .outputs(OutputDef.publicOutput("line", "100 * (fast - slow) / slow"),
                        OutputDef.publicOutput("signal", "EMA of the line"),
                        OutputDef.publicOutput("histogram", "line - signal"))
                .validate(s -> param(s, "fast") < param(s, "slow"), "fast must be smaller than slow")
                .dependencies(s -> List.of(ref(fastEma(s)), ref(slowEma(s))))
                .lookback(s -> 0).warmup(s -> EMA_FACTOR * param(s, "signal"))
                .build((c, s) -> {
                    // slow EMA 0 (e.g. volume of leading zero-volume filler candles): 0, not NaN, which would poison
                    // the recursive signal EMA for ever
                    Indicator<Num> line = formula(c, s, "line", x -> x[1] == 0 ? 0 : (x[0] - x[1]) / x[1] * 100,
                            c.source(ref(fastEma(s))), c.source(ref(slowEma(s))));
                    Indicator<Num> signal = ExponentialBlock.ema(line, param(s, "signal"), c.seed(s, "signal"));
                    Indicator<Num> histogram = formula(c, s, "histogram", x -> x[0] - x[1], line, signal);
                    return Map.of("line", line, "signal", signal, "histogram", histogram);
                });
    }

    private static IndicatorSpec fastEma(IndicatorSpec s) {
        return ema(param(s, "fast"), s.sourceParam(SOURCE));
    }

    private static IndicatorSpec slowEma(IndicatorSpec s) {
        return ema(param(s, "slow"), s.sourceParam(SOURCE));
    }

    /** Aroon up/down: bars since the highest high / lowest low of the last n + 1 bars, in percent. */
    static IndicatorDefinition aroon() {
        return define(AROON).description("Aroon up, down and oscillator").family(TREND).models(WINDOW)
                .params(period(25))
                .outputs(OutputDef.publicOutput("up", "Aroon up 0..100"),
                        OutputDef.publicOutput("down", "Aroon down 0..100"),
                        OutputDef.publicOutput("oscillator", "up - down"))
                .lookback(s -> period(s)).warmup(s -> period(s) + 1)
                .build((c, s) -> Map.of(
                        "up", wrap(c, s, "up", new AroonUpIndicator(c.series(), period(s))),
                        "down", wrap(c, s, "down", new AroonDownIndicator(c.series(), period(s))),
                        "oscillator", wrap(c, s, "oscillator", new AroonOscillatorIndicator(c.series(), period(s)))));
    }

    /**
     * Supertrend (ta4j): bands = HL2 +/- multiplier * ATR, ratcheted against the previous band and close; the line
     * follows the lower band in an up trend and the upper band in a down trend. Bands are internal outputs (seeded).
     */
    static IndicatorDefinition supertrend() {
        return define(SUPERTREND).description("Supertrend").family(TREND).models(RECURSIVE, COMPOSITE)
                .params(period(10), ParamDef.decimal("multiplier", 3, 0.1, 20, "ATR multiplier"))
                .outputs(OutputDef.value("supertrend line"),
                        OutputDef.internal("upperBand", "ratcheted upper band"),
                        OutputDef.internal("lowerBand", "ratcheted lower band"))
                .dependencies(s -> List.of(ref(atr(period(s)))))
                .lookback(s -> 1).warmup(s -> WILDER_FACTOR * period(s))
                .build((c, s) -> {
                    BarSeries series = c.series();
                    int begin = series.getBeginIndex();
                    double m = decimal(s, "multiplier");
                    Indicator<Num> atr = c.source(ref(atr(period(s))));
                    Indicator<Num> hl2 = price(c, PriceField.HL2);
                    Indicator<Num> upper = lambda(c, s, "upperBand", (i, self) -> {
                        double basic = v(hl2, i) + m * v(atr, i);
                        if (i <= begin) {
                            return basic;
                        }
                        double previous = self.applyAsDouble(i - 1);
                        return !(basic < previous) && !(close(series, i - 1) > previous) ? previous : basic;
                    });
                    Indicator<Num> lower = lambda(c, s, "lowerBand", (i, self) -> {
                        if (i <= begin) {
                            return 0;
                        }
                        double previous = self.applyAsDouble(i - 1);
                        double basic = v(hl2, i) - m * v(atr, i);
                        return !(basic > previous) && !(close(series, i - 1) < previous) ? previous : basic;
                    });
                    Indicator<Num> line = lambda(c, s, VALUE, (i, self) -> {
                        if (i <= begin) {
                            return 0;
                        }
                        double close = close(series, i);
                        double previous = self.applyAsDouble(i - 1);
                        double lowerBand = v(lower, i);
                        double upperBand = v(upper, i);
                        double value = 0;
                        if (previous == v(upper, i - 1)) {
                            if (close <= upperBand) {
                                value = upperBand;
                            } else if (close > upperBand) {
                                value = lowerBand;
                            }
                        }
                        if (previous == v(lower, i - 1)) {
                            if (close >= lowerBand) {
                                value = lowerBand;
                            } else if (close < lowerBand) {
                                value = upperBand;
                            }
                        }
                        return value;
                    });
                    return Map.of(VALUE, line, "upperBand", upper, "lowerBand", lower);
                });
    }

    /**
     * Ichimoku lines. Spans are plotted {@code displacement} bars ahead, so the value at a bar is the span computed
     * displacement - 1 bars earlier: never a future value. Chikou (close shifted into the past) is not offered.
     */
    static IndicatorDefinition ichimoku() {
        return define(ICHIMOKU).description("Ichimoku cloud (without chikou)").family(TREND).models(WINDOW, COMPOSITE)
                .params(ParamDef.period("tenkan", 9, "conversion line window"),
                        ParamDef.period("kijun", 26, "base line window"),
                        ParamDef.period("senkouB", 52, "leading span B window"),
                        ParamDef.period("displacement", 26, "forward shift of the spans"))
                .outputs(OutputDef.publicOutput("tenkan", "conversion line"),
                        OutputDef.publicOutput("kijun", "base line"),
                        OutputDef.publicOutput("spanA", "leading span A (shifted)"),
                        OutputDef.publicOutput("spanB", "leading span B (shifted)"))
                .lookback(s -> Math.max(Math.max(param(s, "tenkan"), param(s, "kijun")), param(s, "senkouB"))
                        + param(s, "displacement") - 2)
                .warmup(s -> param(s, "senkouB") + param(s, "displacement"))
                .build((c, s) -> {
                    BarSeries series = c.series();
                    IchimokuTenkanSenIndicator tenkan = new IchimokuTenkanSenIndicator(series, param(s, "tenkan"));
                    IchimokuKijunSenIndicator kijun = new IchimokuKijunSenIndicator(series, param(s, "kijun"));
                    return Map.of(
                            "tenkan", wrap(c, s, "tenkan", tenkan),
                            "kijun", wrap(c, s, "kijun", kijun),
                            "spanA", wrap(c, s, "spanA", new IchimokuSenkouSpanAIndicator(series, tenkan, kijun,
                                    param(s, "displacement"))),
                            "spanB", wrap(c, s, "spanB", new IchimokuSenkouSpanBIndicator(series,
                                    param(s, "senkouB"), param(s, "displacement"))));
                });
    }

    /** Detrended price oscillator: x - SMA(n) of n/2 + 1 bars ago. */
    static IndicatorDefinition dpo() {
        return define(DPO).description("Detrended price oscillator").family(TREND).models(WINDOW)
                .params(period(20), source(PriceField.CLOSE)).outputs(OutputDef.value("oscillator"))
                .lookback(s -> period(s) - 1 + period(s) / 2 + 1).warmup(s -> period(s) + period(s) / 2 + 1)
                .build((c, s) -> single(wrap(c, s, VALUE, new DPOIndicator(source(c, s), period(s)))));
    }

    /** Sum over n bars of EMA(H-L) / EMA(EMA(H-L)); both EMAs are internal outputs (seeded). */
    static IndicatorDefinition massIndex() {
        return define(MASS_INDEX).description("Mass index").family(TREND).models(RECURSIVE, WINDOW)
                .params(ParamDef.period("emaPeriod", 9, "EMA period of the range"),
                        ParamDef.period("sumPeriod", 25, "summed bars"))
                .outputs(OutputDef.value("mass index"),
                        OutputDef.internal("singleEma", "EMA of high - low"),
                        OutputDef.internal("doubleEma", "EMA of the single EMA"))
                .lookback(s -> param(s, "sumPeriod") - 1)
                .warmup(s -> 2 * EMA_FACTOR * param(s, "emaPeriod") + param(s, "sumPeriod"))
                .build((c, s) -> {
                    BarSeries series = c.series();
                    int begin = series.getBeginIndex();
                    int e = param(s, "emaPeriod");
                    int sum = param(s, "sumPeriod");
                    Indicator<Num> range = helper(c, (i, self) -> high(series, i) - low(series, i));
                    Indicator<Num> single = ExponentialBlock.ema(range, e, c.seed(s, "singleEma"));
                    Indicator<Num> dbl = ExponentialBlock.ema(single, e, c.seed(s, "doubleEma"));
                    Indicator<Num> mass = lambda(c, s, VALUE, (i, self) -> {
                        double total = 0;
                        for (int j = Math.max(begin, i - sum + 1); j <= i; j++) {
                            total += v(single, j) / v(dbl, j);
                        }
                        return total;
                    });
                    return Map.of(VALUE, mass, "singleEma", single, "doubleEma", dbl);
                });
    }

    /**
     * Least-squares line over n bars: fitted value at the current bar and slope per bar. The intercept is not offered:
     * ta4j measures x as the absolute bar index, which differs between a seeded job and a full computation.
     */
    static IndicatorDefinition linearRegression() {
        return define(LINREG).description("Linear regression value and slope").family(TREND).models(WINDOW)
                .params(period(20), source(PriceField.CLOSE))
                .outputs(OutputDef.value("fitted value at the current bar"),
                        OutputDef.publicOutput("slope", "slope per bar"))
                .validate(s -> period(s) >= 2, "period must be at least 2")
                .lookback(s -> period(s) - 1).warmup(s -> period(s))
                .build((c, s) -> Map.of(
                        VALUE, wrap(c, s, VALUE, new SimpleLinearRegressionIndicator(source(c, s), period(s),
                                SimpleLinearRegressionType.Y)),
                        "slope", wrap(c, s, "slope", new SimpleLinearRegressionIndicator(source(c, s), period(s),
                                SimpleLinearRegressionType.SLOPE))));
    }
}
