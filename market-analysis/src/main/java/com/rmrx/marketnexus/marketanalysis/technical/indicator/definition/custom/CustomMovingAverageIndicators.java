package com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.custom;

import com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.BuildContext;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.IndicatorDefinition;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.OutputDef;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.type.MovingAverageIndicators;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.schema.ParamDef;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.spec.IndicatorSpec;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.spec.PriceField;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.spec.Source;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.ComputationModel.COMPOSITE;
import static com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.ComputationModel.RECURSIVE;
import static com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.ComputationModel.WINDOW;
import static com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.DefinitionBuilder.define;
import static com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.IndicatorFamily.MOVING_AVERAGE;
import static com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.type.DefinitionSupport.*;

/** Moving averages without a ta4j indicator. */
public final class CustomMovingAverageIndicators {

    public static final String T3 = "T3";
    public static final String ALMA = "ALMA";
    public static final String FRAMA = "FRAMA";
    public static final String ALLIGATOR = "ALLIGATOR";

    private CustomMovingAverageIndicators() {
    }

    public static List<IndicatorDefinition> definitions() {
        return List.of(t3(), alma(), frama(), alligator());
    }

    /**
     * Williams Alligator (Bill Williams / TradingView): jaw, teeth and lips are SMMAs (Wilder RMA, shared RMA nodes) of
     * HL2, each drawn shifted forward. A forward shift by k bars means the line's value at bar i is the SMMA of bar
     * i - k: a lagged value known at bar i, no future data. NaN until the shifted bar exists. The SMMA starts with the
     * first value (ta4j convention; TradingView starts it with an SMA, which converges to the same values).
     */
    static IndicatorDefinition alligator() {
        return define(ALLIGATOR).description("Williams Alligator").family(MOVING_AVERAGE).models(RECURSIVE, COMPOSITE)
                .params(ParamDef.period("jaw", 13, "jaw SMMA period"),
                        ParamDef.integer("jawShift", 8, 0, 100, "jaw shift in bars"),
                        ParamDef.period("teeth", 8, "teeth SMMA period"),
                        ParamDef.integer("teethShift", 5, 0, 100, "teeth shift in bars"),
                        ParamDef.period("lips", 5, "lips SMMA period"),
                        ParamDef.integer("lipsShift", 3, 0, 100, "lips shift in bars"),
                        source(PriceField.HL2))
                .outputs(OutputDef.publicOutput("jaw", "SMMA(jaw) shifted jawShift bars"),
                        OutputDef.publicOutput("teeth", "SMMA(teeth) shifted teethShift bars"),
                        OutputDef.publicOutput("lips", "SMMA(lips) shifted lipsShift bars"))
                .dependencies(s -> List.of(ref(smma(s, "jaw")), ref(smma(s, "teeth")), ref(smma(s, "lips"))))
                .lookback(s -> maxShift(s)).warmup(s -> maxShift(s))
                .build((c, s) -> {
                    int begin = c.series().getBeginIndex();
                    return Map.of(
                            "jaw", shifted(c, s, "jaw", begin),
                            "teeth", shifted(c, s, "teeth", begin),
                            "lips", shifted(c, s, "lips", begin));
                });
    }

    private static IndicatorSpec smma(IndicatorSpec spec, String line) {
        return IndicatorSpec.of(MovingAverageIndicators.RMA).with(PERIOD, param(spec, line))
                .with(SOURCE, spec.sourceParam(SOURCE));
    }

    private static int maxShift(IndicatorSpec spec) {
        return Math.max(param(spec, "jawShift"), Math.max(param(spec, "teethShift"), param(spec, "lipsShift")));
    }

    private static Indicator<Num> shifted(BuildContext c, IndicatorSpec s, String line, int begin) {
        Indicator<Num> average = c.source(ref(smma(s, line)));
        int shift = param(s, line + "Shift");
        return lambda(c, s, line, (i, self) -> i - shift < begin ? Double.NaN : v(average, i - shift));
    }

    /**
     * Arnaud Legoux moving average (pandas-ta {@code alma}, TradingView {@code ta.alma}): Gaussian-weighted window,
     * weight of the k-th oldest bar (k = 0..n-1) = exp(-(k - m)^2 / (2 s^2)), m = offset * (n - 1), s = n / sigma.
     * NaN until n bars exist.
     */
    static IndicatorDefinition alma() {
        return define(ALMA).description("Arnaud Legoux moving average").family(MOVING_AVERAGE).models(WINDOW)
                .params(period(9), ParamDef.decimal("offset", 0.85, 0, 1, "position of the Gaussian peak"),
                        ParamDef.decimal("sigma", 6, 0.1, 100, "width of the Gaussian"), source(PriceField.CLOSE))
                .outputs(OutputDef.value("average"))
                .lookback(s -> period(s) - 1).warmup(s -> period(s))
                .build((c, s) -> {
                    Indicator<Num> source = source(c, s);
                    int begin = c.series().getBeginIndex();
                    int n = period(s);
                    double m = decimal(s, "offset") * (n - 1);
                    double width = n / decimal(s, "sigma");
                    double[] weights = new double[n];
                    double total = 0;
                    for (int k = 0; k < n; k++) {
                        weights[k] = Math.exp(-(k - m) * (k - m) / (2 * width * width));
                        total += weights[k];
                    }
                    double weightSum = total;
                    return single(lambda(c, s, VALUE, (i, self) -> {
                        int first = i - n + 1;
                        if (first < begin) {
                            return Double.NaN;
                        }
                        double sum = 0;
                        for (int k = 0; k < n; k++) {
                            sum += weights[k] * v(source, first + k);
                        }
                        return sum / weightSum;
                    }));
                });
    }

    /**
     * Fractal adaptive moving average (Ehlers): N1 / N2 = (highest high - lowest low) / (n/2) of the newer / older half,
     * N3 = the same over all n bars, D = (ln(N1 + N2) - ln(N3)) / ln 2, alpha = exp(-4.6 (D - 1)) clamped to 0.01..1,
     * value = alpha * price + (1 - alpha) * previous. A flat window uses alpha 0.01. Price defaults to HL2 (Ehlers).
     * Started with the price until n bars exist (below the lookback, so never in a seeded job).
     */
    static IndicatorDefinition frama() {
        return define(FRAMA).description("Fractal adaptive moving average").family(MOVING_AVERAGE)
                .models(RECURSIVE, WINDOW)
                .params(ParamDef.integer(PERIOD, 16, 2, 1000, "bars, even"), source(PriceField.HL2))
                .outputs(OutputDef.value("average"))
                .validate(s -> period(s) % 2 == 0, "period must be even")
                .lookback(s -> period(s) - 1).warmup(s -> 10 * period(s))
                .build((c, s) -> {
                    BarSeries series = c.series();
                    Indicator<Num> source = source(c, s);
                    int begin = series.getBeginIndex();
                    int n = period(s);
                    int half = n / 2;
                    return single(lambda(c, s, VALUE, (i, self) -> {
                        double price = v(source, i);
                        int first = i - n + 1;
                        if (first < begin) {
                            return price;
                        }
                        double n1 = range(series, i - half + 1, i) / half;
                        double n2 = range(series, first, i - half) / half;
                        double n3 = range(series, first, i) / n;
                        double alpha = 0.01;
                        if (n1 + n2 > 0 && n3 > 0) {
                            double dimension = (Math.log(n1 + n2) - Math.log(n3)) / Math.log(2);
                            alpha = Math.min(1, Math.max(0.01, Math.exp(-4.6 * (dimension - 1))));
                        }
                        return alpha * price + (1 - alpha) * self.applyAsDouble(i - 1);
                    }));
                });
    }

    /** Highest high - lowest low of bars from..to (inclusive). */
    private static double range(BarSeries series, int from, int to) {
        double highest = Double.NEGATIVE_INFINITY;
        double lowest = Double.POSITIVE_INFINITY;
        for (int j = from; j <= to; j++) {
            highest = Math.max(highest, high(series, j));
            lowest = Math.min(lowest, low(series, j));
        }
        return highest - lowest;
    }

    /**
     * Tillson T3 (TA-Lib {@code T3}, pandas-ta {@code t3}): six chained EMAs e1..e6 (shared nodes, e(k) = EMA(e(k-1))),
     * T3 = c1*e6 + c2*e5 + c3*e4 + c4*e3 with a = vfactor, c1 = -a^3, c2 = 3a^2 + 3a^3, c3 = -6a^2 - 3a - 3a^3,
     * c4 = 1 + 3a + a^3 + 3a^2. EMAs start with the first input (ta4j); TA-Lib starts them with an SMA.
     */
    static IndicatorDefinition t3() {
        return define(T3).description("Tillson T3 moving average").family(MOVING_AVERAGE).models(RECURSIVE, COMPOSITE)
                .params(period(5), ParamDef.decimal("vfactor", 0.7, 0, 1, "volume factor"), source(PriceField.CLOSE))
                .outputs(OutputDef.value("average"))
                .dependencies(s -> emaChain(s).stream().map(e -> ref(e)).toList())
                .lookback(s -> 0).warmup(s -> 0)
                .build((c, s) -> {
                    List<IndicatorSpec> chain = emaChain(s);
                    double a = decimal(s, "vfactor");
                    double c1 = -a * a * a;
                    double c2 = 3 * a * a + 3 * a * a * a;
                    double c3 = -6 * a * a - 3 * a - 3 * a * a * a;
                    double c4 = 1 + 3 * a + a * a * a + 3 * a * a;
                    return single(formula(c, s, VALUE, x -> c1 * x[3] + c2 * x[2] + c3 * x[1] + c4 * x[0],
                            c.source(ref(chain.get(2))), c.source(ref(chain.get(3))),
                            c.source(ref(chain.get(4))), c.source(ref(chain.get(5)))));
                });
    }

    /** e1..e6: EMA of the source, then each an EMA of the previous one. */
    private static List<IndicatorSpec> emaChain(IndicatorSpec spec) {
        List<IndicatorSpec> chain = new ArrayList<>(6);
        Source source = spec.sourceParam(SOURCE);
        for (int k = 0; k < 6; k++) {
            IndicatorSpec ema = ema(period(spec), source);
            chain.add(ema);
            source = ref(ema);
        }
        return chain;
    }
}
