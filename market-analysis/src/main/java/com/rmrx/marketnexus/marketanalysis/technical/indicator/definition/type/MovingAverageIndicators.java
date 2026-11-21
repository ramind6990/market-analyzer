package com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.type;

import com.rmrx.marketnexus.marketanalysis.technical.indicator.block.ExponentialBlock;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.IndicatorDefinition;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.OutputDef;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.schema.ParamDef;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.spec.IndicatorSpec;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.spec.PriceField;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.averages.HMAIndicator;
import org.ta4j.core.indicators.averages.LSMAIndicator;
import org.ta4j.core.indicators.averages.VWMAIndicator;
import org.ta4j.core.indicators.averages.WMAIndicator;
import org.ta4j.core.num.Num;

import java.util.List;

import static com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.ComputationModel.COMPOSITE;
import static com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.ComputationModel.RECURSIVE;
import static com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.ComputationModel.WINDOW;
import static com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.DefinitionBuilder.define;
import static com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.IndicatorFamily.MOVING_AVERAGE;
import static com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.type.DefinitionSupport.*;

/** Moving averages besides SMA and EMA (catalog section 3.2). Formulas follow ta4j 0.18. */
public final class MovingAverageIndicators {

    public static final String RMA = "RMA";
    public static final String WMA = "WMA";
    public static final String HMA = "HMA";
    public static final String DEMA = "DEMA";
    public static final String TEMA = "TEMA";
    public static final String ZLEMA = "ZLEMA";
    public static final String KAMA = "KAMA";
    public static final String VIDYA = "VIDYA";
    public static final String MCGINLEY = "MCGINLEY";
    public static final String VWMA = "VWMA";
    public static final String LSMA = "LSMA";

    private MovingAverageIndicators() {
    }

    public static List<IndicatorDefinition> definitions() {
        return List.of(rma(), wma(), hma(), dema(), tema(), zlema(), kama(), vidya(), mcGinley(), vwma(), lsma());
    }

    /** Wilder's smoothing (RMA / SMMA / MMA), alpha = 1/n, started with the first input. */
    static IndicatorDefinition rma() {
        return define(RMA).description("Wilder moving average (RMA/SMMA)").family(MOVING_AVERAGE).models(RECURSIVE)
                .params(period(14), source(PriceField.CLOSE)).outputs(OutputDef.value("average"))
                .lookback(s -> 0).warmup(s -> WILDER_FACTOR * period(s))
                .build((c, s) -> single(ExponentialBlock.wilder(source(c, s), period(s), c.seed(s, VALUE))));
    }

    static IndicatorDefinition wma() {
        return define(WMA).description("Linearly weighted moving average").family(MOVING_AVERAGE).models(WINDOW)
                .params(period(20), source(PriceField.CLOSE)).outputs(OutputDef.value("average"))
                .lookback(s -> period(s) - 1).warmup(s -> period(s))
                .build((c, s) -> single(wrap(c, s, VALUE, new WMAIndicator(source(c, s), period(s)))));
    }

    /** Hull: WMA(sqrt n) of 2 * WMA(n/2) - WMA(n). */
    static IndicatorDefinition hma() {
        return define(HMA).description("Hull moving average").family(MOVING_AVERAGE).models(WINDOW, COMPOSITE)
                .params(period(9), source(PriceField.CLOSE)).outputs(OutputDef.value("average"))
                .lookback(s -> period(s) - 1 + (int) Math.sqrt(period(s)) - 1)
                .warmup(s -> period(s) + (int) Math.sqrt(period(s)))
                .validate(s -> period(s) >= 2, "period must be at least 2")
                .build((c, s) -> single(wrap(c, s, VALUE, new HMAIndicator(source(c, s), period(s)))));
    }

    /** 2 * EMA - EMA(EMA); both EMAs are shared nodes (the second one has the first as source). */
    static IndicatorDefinition dema() {
        return define(DEMA).description("Double exponential moving average").family(MOVING_AVERAGE)
                .models(RECURSIVE, COMPOSITE)
                .params(period(20), source(PriceField.CLOSE)).outputs(OutputDef.value("average"))
                .dependencies(s -> List.of(ref(ema1(s)), ref(ema2(s))))
                .lookback(s -> 0).warmup(s -> 0)
                .build((c, s) -> single(formula(c, s, VALUE, x -> x[0] * 2 - x[1],
                        c.source(ref(ema1(s))), c.source(ref(ema2(s))))));
    }

    /** 3 * E1 - 3 * E2 + E3 with E2 = EMA(E1), E3 = EMA(E2). */
    static IndicatorDefinition tema() {
        return define(TEMA).description("Triple exponential moving average").family(MOVING_AVERAGE)
                .models(RECURSIVE, COMPOSITE)
                .params(period(20), source(PriceField.CLOSE)).outputs(OutputDef.value("average"))
                .dependencies(s -> List.of(ref(ema1(s)), ref(ema2(s)), ref(ema3(s))))
                .lookback(s -> 0).warmup(s -> 0)
                .build((c, s) -> single(formula(c, s, VALUE, x -> 3 * (x[0] - x[1]) + x[2],
                        c.source(ref(ema1(s))), c.source(ref(ema2(s))), c.source(ref(ema3(s))))));
    }

    private static IndicatorSpec ema1(IndicatorSpec s) {
        return ema(period(s), s.sourceParam(SOURCE));
    }

    private static IndicatorSpec ema2(IndicatorSpec s) {
        return ema(period(s), ref(ema1(s)));
    }

    private static IndicatorSpec ema3(IndicatorSpec s) {
        return ema(period(s), ref(ema2(s)));
    }

    /**
     * Zero-lag EMA (ta4j): partial SMA during the first n - 1 bars, then
     * k * (2 * x(i) - x(i - lag)) + (1 - k) * previous, lag = (n - 1) / 2. Lookback n - 1 keeps the start branch out
     * of seeded jobs.
     */
    static IndicatorDefinition zlema() {
        return define(ZLEMA).description("Zero-lag exponential moving average").family(MOVING_AVERAGE)
                .models(RECURSIVE)
                .params(period(20), source(PriceField.CLOSE)).outputs(OutputDef.value("average"))
                .lookback(s -> period(s) - 1).warmup(s -> EMA_FACTOR * period(s))
                .build((c, s) -> {
                    int n = period(s);
                    int lag = (n - 1) / 2;
                    double k = 2.0 / (n + 1);
                    Indicator<Num> x = source(c, s);
                    int begin = c.series().getBeginIndex();
                    return single(lambda(c, s, VALUE, (i, self) -> {
                        if (i - begin + 1 < n) {
                            return mean(x, i, n, begin);
                        }
                        return k * (2 * v(x, i) - v(x, i - lag)) + (1 - k) * self.applyAsDouble(i - 1);
                    }));
                });
    }

    /**
     * Kaufman adaptive MA: efficiency ratio over n bars scales between fast and slow EMA constants.
     * Deviation from ta4j: a window without any movement gives ratio 0 (ta4j: NaN for ever after).
     */
    static IndicatorDefinition kama() {
        return define(KAMA).description("Kaufman adaptive moving average").family(MOVING_AVERAGE)
                .models(RECURSIVE, WINDOW)
                .params(period(10), ParamDef.period("fast", 2, "fast EMA period"),
                        ParamDef.period("slow", 30, "slow EMA period"), source(PriceField.CLOSE))
                .outputs(OutputDef.value("average"))
                .lookback(s -> period(s)).warmup(s -> period(s) + EMA_FACTOR * param(s, "slow"))
                .build((c, s) -> {
                    int n = period(s);
                    double fastest = 2.0 / (param(s, "fast") + 1);
                    double slowest = 2.0 / (param(s, "slow") + 1);
                    Indicator<Num> x = source(c, s);
                    int begin = c.series().getBeginIndex();
                    return single(lambda(c, s, VALUE, (i, self) -> {
                        double price = v(x, i);
                        if (i - begin < n) {
                            return price;
                        }
                        int start = i - n;
                        double change = Math.abs(price - v(x, start));
                        double volatility = 0;
                        for (int j = start; j < i; j++) {
                            volatility += Math.abs(v(x, j + 1) - v(x, j));
                        }
                        double er = volatility == 0 ? 0 : change / volatility;
                        double sc = Math.pow(er * (fastest - slowest) + slowest, 2);
                        double prior = self.applyAsDouble(i - 1);
                        return prior + sc * (price - prior);
                    }));
                });
    }

    /**
     * Chande's VIDYA as in ta4j: previous + |CMO(cmoPeriod)| / 100 * (x - previous).
     * Deviation from ta4j: a flat CMO window gives CMO 0 (ta4j: NaN for ever after).
     */
    static IndicatorDefinition vidya() {
        return define(VIDYA).description("Variable index dynamic average (CMO-adaptive)").family(MOVING_AVERAGE)
                .models(RECURSIVE, WINDOW)
                .params(ParamDef.period("cmoPeriod", 9, "CMO window"), source(PriceField.CLOSE))
                .outputs(OutputDef.value("average"))
                .lookback(s -> param(s, "cmoPeriod")).warmup(s -> 10 * param(s, "cmoPeriod"))
                .build((c, s) -> {
                    int m = param(s, "cmoPeriod");
                    Indicator<Num> x = source(c, s);
                    int begin = c.series().getBeginIndex();
                    return single(lambda(c, s, VALUE, (i, self) -> {
                        if (i - begin < 1) {
                            return v(x, i);
                        }
                        double gains = 0;
                        double losses = 0;
                        for (int j = Math.max(begin + 1, i - m + 1); j <= i; j++) {
                            double change = v(x, j) - v(x, j - 1);
                            gains += Math.max(0, change);
                            losses += Math.max(0, -change);
                        }
                        double total = gains + losses;
                        double cmo = total == 0 ? 0 : (gains - losses) / total * 100;
                        double previous = self.applyAsDouble(i - 1);
                        return previous + Math.abs(cmo) / 100 * (v(x, i) - previous);
                    }));
                });
    }

    /** McGinley dynamic: previous + (x - previous) / (n * (x / previous)^2). */
    static IndicatorDefinition mcGinley() {
        return define(MCGINLEY).description("McGinley dynamic").family(MOVING_AVERAGE).models(RECURSIVE)
                .params(period(14), source(PriceField.CLOSE)).outputs(OutputDef.value("average"))
                .lookback(s -> 0).warmup(s -> EMA_FACTOR * period(s))
                .build((c, s) -> {
                    int n = period(s);
                    Indicator<Num> x = source(c, s);
                    int begin = c.series().getBeginIndex();
                    return single(lambda(c, s, VALUE, (i, self) -> {
                        if (i <= begin) {
                            return v(x, i);
                        }
                        double previous = self.applyAsDouble(i - 1);
                        double price = v(x, i);
                        return previous + (price - previous) / (n * Math.pow(price / previous, 2));
                    }));
                });
    }

    /** SMA(price * volume) / SMA(volume). */
    static IndicatorDefinition vwma() {
        return define(VWMA).description("Volume-weighted moving average").family(MOVING_AVERAGE).models(WINDOW)
                .params(period(20), source(PriceField.CLOSE)).outputs(OutputDef.value("average"))
                .lookback(s -> period(s) - 1).warmup(s -> period(s))
                .build((c, s) -> single(wrap(c, s, VALUE, new VWMAIndicator(source(c, s), period(s)))));
    }

    /** End point of the least-squares line over n bars. */
    static IndicatorDefinition lsma() {
        return define(LSMA).description("Least squares moving average").family(MOVING_AVERAGE).models(WINDOW)
                .params(period(25), source(PriceField.CLOSE)).outputs(OutputDef.value("average"))
                .lookback(s -> period(s) - 1).warmup(s -> period(s))
                .build((c, s) -> single(wrap(c, s, VALUE, new LSMAIndicator(source(c, s), period(s)))));
    }
}
