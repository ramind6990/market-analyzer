package com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.custom;

import com.rmrx.marketnexus.marketanalysis.technical.indicator.block.ChangeBlock;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.block.ExponentialBlock;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.block.Seed;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.block.SmaBlock;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.block.WindowExtremeBlock;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.IndicatorDefinition;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.OutputDef;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.type.RsiDefinition;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.schema.ParamDef;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.spec.IndicatorSpec;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.spec.PriceField;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;

import java.util.List;
import java.util.Map;
import java.util.function.IntToDoubleFunction;

import static com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.ComputationModel.COMPOSITE;
import static com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.ComputationModel.RECURSIVE;
import static com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.ComputationModel.STATELESS;
import static com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.ComputationModel.WINDOW;
import static com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.DefinitionBuilder.define;
import static com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.IndicatorFamily.MOMENTUM;
import static com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.type.DefinitionSupport.*;

/** Momentum indicators without a ta4j indicator. */
public final class CustomMomentumIndicators {

    public static final String BOP = "BOP";
    public static final String TSI = "TSI";
    public static final String CONNORS_RSI = "CONNORS_RSI";
    public static final String ULTOSC = "ULTOSC";
    public static final String RVGI = "RVGI";
    public static final String SQUEEZE_MOMENTUM = "SQUEEZE_MOMENTUM";

    private CustomMomentumIndicators() {
    }

    public static List<IndicatorDefinition> definitions() {
        return List.of(balanceOfPower(), trueStrengthIndex(), connorsRsi(), ultimateOscillator(), relativeVigor(),
                squeezeMomentum());
    }

    /**
     * Ultimate oscillator (Williams; TA-Lib {@code ULTOSC}, pandas-ta {@code uo}): BP = close - min(low, previous
     * close), TR = max(high, previous close) - min(low, previous close); avg(k) = sum BP / sum TR over k bars;
     * value = 100 * (4 avg(short) + 2 avg(medium) + avg(long)) / 7. A window with TR sum 0 counts as avg 0.
     */
    static IndicatorDefinition ultimateOscillator() {
        return define(ULTOSC).description("Ultimate oscillator").family(MOMENTUM).models(WINDOW)
                .params(ParamDef.period("short", 7, "short window"), ParamDef.period("medium", 14, "medium window"),
                        ParamDef.period("long", 28, "long window"))
                .outputs(OutputDef.value("UO 0..100"))
                .validate(s -> param(s, "short") < param(s, "medium") && param(s, "medium") < param(s, "long"),
                        "windows must be short < medium < long")
                .lookback(s -> param(s, "long")).warmup(s -> param(s, "long") + 1)
                .build((c, s) -> {
                    BarSeries series = c.series();
                    int begin = series.getBeginIndex();
                    int shortWindow = param(s, "short");
                    int mediumWindow = param(s, "medium");
                    int longWindow = param(s, "long");
                    return single(lambda(c, s, VALUE, (i, self) -> {
                        if (i - longWindow < begin) {
                            return Double.NaN;
                        }
                        double weighted = 4 * buyingPressureAverage(series, i, shortWindow)
                                + 2 * buyingPressureAverage(series, i, mediumWindow)
                                + buyingPressureAverage(series, i, longWindow);
                        return 100 * weighted / 7;
                    }));
                });
    }

    private static double buyingPressureAverage(BarSeries series, int i, int window) {
        double pressure = 0;
        double trueRange = 0;
        for (int j = i - window + 1; j <= i; j++) {
            double previousClose = close(series, j - 1);
            double trueLow = Math.min(low(series, j), previousClose);
            pressure += close(series, j) - trueLow;
            trueRange += Math.max(high(series, j), previousClose) - trueLow;
        }
        return trueRange == 0 ? 0 : pressure / trueRange;
    }

    /**
     * Relative vigor index (Ehlers; TradingView {@code RVGI}, pandas-ta {@code rvgi} with swma length 4):
     * value = sum over n of SWMA(close - open) / sum over n of SWMA(high - low), SWMA = symmetric weights 1,2,2,1 / 6;
     * signal = SWMA of the value. 0 when the range sum is 0.
     */
    static IndicatorDefinition relativeVigor() {
        return define(RVGI).description("Relative vigor index").family(MOMENTUM).models(WINDOW)
                .params(period(10))
                .outputs(OutputDef.value("RVGI"), OutputDef.publicOutput("signal", "SWMA of the value"))
                .lookback(s -> period(s) + 2).warmup(s -> period(s) + 6)
                .build((c, s) -> {
                    BarSeries series = c.series();
                    int begin = series.getBeginIndex();
                    int n = period(s);
                    Indicator<Num> value = lambda(c, s, VALUE, (i, self) -> {
                        if (i - n - 2 < begin) {
                            return Double.NaN;
                        }
                        double numerator = 0;
                        double denominator = 0;
                        for (int j = i - n + 1; j <= i; j++) {
                            numerator += swma(k -> close(series, k) - open(series, k), j);
                            denominator += swma(k -> high(series, k) - low(series, k), j);
                        }
                        return denominator == 0 ? 0 : numerator / denominator;
                    });
                    Indicator<Num> signal = lambda(c, s, "signal", (i, self) ->
                            i - 3 < begin ? Double.NaN : swma(k -> v(value, k), i));
                    return Map.of(VALUE, value, "signal", signal);
                });
    }

    /** Symmetric weighted average of bars j-3..j with weights 1, 2, 2, 1 (newest last). */
    private static double swma(IntToDoubleFunction x, int j) {
        return (x.applyAsDouble(j - 3) + 2 * x.applyAsDouble(j - 2) + 2 * x.applyAsDouble(j - 1) + x.applyAsDouble(j))
                / 6;
    }

    /**
     * Squeeze momentum (LazyBear; pandas-ta {@code squeeze} with {@code lazybear=True}): d = close - (midpoint of
     * highest high / lowest low over n + SMA(close, n)) / 2; value = least-squares line of d over the last n bars,
     * evaluated at the newest bar (x relative to the window, so seeded and full jobs agree).
     */
    static IndicatorDefinition squeezeMomentum() {
        return define(SQUEEZE_MOMENTUM).description("Squeeze momentum (LazyBear)").family(MOMENTUM).models(WINDOW)
                .params(period(20)).outputs(OutputDef.value("momentum"))
                .lookback(s -> 2 * period(s) - 2).warmup(s -> 2 * period(s) - 1)
                .build((c, s) -> {
                    BarSeries series = c.series();
                    int begin = series.getBeginIndex();
                    int n = period(s);
                    Indicator<Num> closePrice = price(c, PriceField.CLOSE);
                    Indicator<Num> highest = WindowExtremeBlock.highest(price(c, PriceField.HIGH), n, Seed.none());
                    Indicator<Num> lowest = WindowExtremeBlock.lowest(price(c, PriceField.LOW), n, Seed.none());
                    Indicator<Num> sma = new SmaBlock(closePrice, n, Seed.none());
                    Indicator<Num> deviation = helper(c, (j, self) ->
                            v(closePrice, j) - ((v(highest, j) + v(lowest, j)) / 2 + v(sma, j)) / 2);
                    return single(lambda(c, s, VALUE, (i, self) -> {
                        int first = i - n + 1;
                        if (first - n + 1 < begin) {
                            return Double.NaN;
                        }
                        double sumX = 0;
                        double sumY = 0;
                        double sumXY = 0;
                        double sumXX = 0;
                        for (int x = 0; x < n; x++) {
                            double y = v(deviation, first + x);
                            sumX += x;
                            sumY += y;
                            sumXY += x * y;
                            sumXX += (double) x * x;
                        }
                        double divisor = n * sumXX - sumX * sumX;
                        double slope = divisor == 0 ? 0 : (n * sumXY - sumX * sumY) / divisor;
                        double intercept = (sumY - slope * sumX) / n;
                        return intercept + slope * (n - 1);
                    }));
                });
    }

    /**
     * Balance of power (TA-Lib {@code BOP}, pandas-ta {@code bop}): (close - open) / (high - low), 0 for a bar without
     * range (TA-Lib: 0 as well). Plus its SMA, as commonly plotted.
     */
    static IndicatorDefinition balanceOfPower() {
        return define(BOP).description("Balance of power").family(MOMENTUM).models(STATELESS, WINDOW)
                .params(period(14))
                .outputs(OutputDef.value("raw balance of power -1..1"),
                        OutputDef.publicOutput("smoothed", "SMA of the raw value"))
                .lookback(s -> period(s) - 1).warmup(s -> period(s))
                .build((c, s) -> {
                    BarSeries series = c.series();
                    Indicator<Num> raw = lambda(c, s, VALUE, (i, self) -> {
                        double range = high(series, i) - low(series, i);
                        return range == 0 ? 0 : (close(series, i) - open(series, i)) / range;
                    });
                    return Map.of(VALUE, raw, "smoothed", new SmaBlock(raw, period(s), c.seed(s, "smoothed")));
                });
    }

    /**
     * True strength index (Blau; pandas-ta {@code tsi}): 100 * EMA(short, EMA(long, momentum)) /
     * EMA(short, EMA(long, |momentum|)), momentum = x - previous x (0 on the first bar); plus an EMA signal line.
     * The four EMAs are recursive state (INTERNAL outputs). 0 while the absolute momentum average is 0.
     */
    static IndicatorDefinition trueStrengthIndex() {
        return define(TSI).description("True strength index").family(MOMENTUM).models(RECURSIVE)
                .params(ParamDef.period("long", 25, "first EMA period"), ParamDef.period("short", 13, "second EMA period"),
                        ParamDef.period("signal", 13, "EMA period of the signal line"), source(PriceField.CLOSE))
                .outputs(OutputDef.value("TSI -100..100"), OutputDef.publicOutput("signal", "EMA of TSI"),
                        OutputDef.internal("momLong", "EMA(long) of momentum"),
                        OutputDef.internal("momShort", "EMA(short) of momLong"),
                        OutputDef.internal("absLong", "EMA(long) of |momentum|"),
                        OutputDef.internal("absShort", "EMA(short) of absLong"))
                .lookback(s -> 1)
                .warmup(s -> EMA_FACTOR * (param(s, "long") + param(s, "short") + param(s, "signal")) + 1)
                .build((c, s) -> {
                    Indicator<Num> source = source(c, s);
                    int begin = c.series().getBeginIndex();
                    int longPeriod = param(s, "long");
                    int shortPeriod = param(s, "short");
                    Indicator<Num> momentum = helper(c, (i, self) -> i <= begin ? 0 : v(source, i) - v(source, i - 1));
                    Indicator<Num> absMomentum = helper(c, (i, self) -> Math.abs(v(momentum, i)));
                    Indicator<Num> momLong = ExponentialBlock.ema(momentum, longPeriod, c.seed(s, "momLong"));
                    Indicator<Num> momShort = ExponentialBlock.ema(momLong, shortPeriod, c.seed(s, "momShort"));
                    Indicator<Num> absLong = ExponentialBlock.ema(absMomentum, longPeriod, c.seed(s, "absLong"));
                    Indicator<Num> absShort = ExponentialBlock.ema(absLong, shortPeriod, c.seed(s, "absShort"));
                    Indicator<Num> tsi = formula(c, s, VALUE, x -> x[1] == 0 ? 0 : 100 * x[0] / x[1],
                            momShort, absShort);
                    return Map.of(VALUE, tsi,
                            "signal", ExponentialBlock.ema(tsi, param(s, "signal"), c.seed(s, "signal")),
                            "momLong", momLong, "momShort", momShort, "absLong", absLong, "absShort", absShort);
                });
    }

    /**
     * Connors RSI (Connors Research / TradingView {@code ta.crsi}): average of RSI(close, rsi), RSI(streak, streak) and
     * the percent rank of the one-bar % change over {@code rank} bars. Streak = consecutive up (+1, +2, ..) or down
     * (-1, -2, ..) closes, 0 on an unchanged close. Percent rank = % of the previous {@code rank} changes that are
     * less than or equal to the current one (TradingView {@code ta.percentrank}). RSI(close) is the shared RSI node;
     * streak and the streak RSI's Wilder averages are recursive state (INTERNAL outputs).
     */
    static IndicatorDefinition connorsRsi() {
        return define(CONNORS_RSI).description("Connors RSI").family(MOMENTUM).models(RECURSIVE, WINDOW, COMPOSITE)
                .params(ParamDef.period("rsi", 3, "RSI period of the close"),
                        ParamDef.period("streak", 2, "RSI period of the streak"),
                        ParamDef.period("rank", 100, "percent rank window"))
                .outputs(OutputDef.value("Connors RSI 0..100"),
                        OutputDef.internal("streak", "consecutive up/down closes"),
                        OutputDef.internal("streakAvgGain", "Wilder average of streak gains"),
                        OutputDef.internal("streakAvgLoss", "Wilder average of streak losses"))
                .dependencies(s -> List.of(ref(closeRsi(s))))
                .lookback(s -> param(s, "rank") + 1)
                .warmup(s -> WILDER_FACTOR * param(s, "streak") + param(s, "rank") + 1)
                .build((c, s) -> {
                    BarSeries series = c.series();
                    int begin = series.getBeginIndex();
                    int rank = param(s, "rank");
                    int streakPeriod = param(s, "streak");
                    Indicator<Num> streak = lambda(c, s, "streak", (i, self) -> {
                        if (i <= begin) {
                            return 0;
                        }
                        double change = close(series, i) - close(series, i - 1);
                        double previous = self.applyAsDouble(i - 1);
                        if (change > 0) {
                            return previous > 0 ? previous + 1 : 1;
                        }
                        if (change < 0) {
                            return previous < 0 ? previous - 1 : -1;
                        }
                        return 0;
                    });
                    Indicator<Num> avgGain = ExponentialBlock.wilder(ChangeBlock.gain(streak, Seed.none()),
                            streakPeriod, c.seed(s, "streakAvgGain"));
                    Indicator<Num> avgLoss = ExponentialBlock.wilder(ChangeBlock.loss(streak, Seed.none()),
                            streakPeriod, c.seed(s, "streakAvgLoss"));
                    Indicator<Num> change = helper(c, (i, self) -> {
                        if (i <= begin) {
                            return 0;
                        }
                        double previous = close(series, i - 1);
                        return previous == 0 ? 0 : 100 * (close(series, i) - previous) / previous;
                    });
                    Indicator<Num> percentRank = helper(c, (i, self) -> {
                        int start = Math.max(begin + 1, i - rank);
                        if (start >= i) {
                            return 0;
                        }
                        double current = v(change, i);
                        int count = 0;
                        for (int j = start; j < i; j++) {
                            if (v(change, j) <= current) {
                                count++;
                            }
                        }
                        return 100.0 * count / (i - start);
                    });
                    Indicator<Num> crsi = formula(c, s, VALUE, x -> (x[0] + rsi(x[1], x[2]) + x[3]) / 3,
                            c.source(ref(closeRsi(s))), avgGain, avgLoss, percentRank);
                    return Map.of(VALUE, crsi, "streak", streak, "streakAvgGain", avgGain, "streakAvgLoss", avgLoss);
                });
    }

    private static IndicatorSpec closeRsi(IndicatorSpec spec) {
        return IndicatorSpec.of(RsiDefinition.TYPE).with(RsiDefinition.PERIOD, param(spec, "rsi"))
                .with(RsiDefinition.SOURCE, PriceField.CLOSE);
    }

    /** RSI from Wilder averages, same edge cases as {@code RsiDefinition}. */
    private static double rsi(double gain, double loss) {
        if (loss == 0) {
            return gain == 0 ? 0 : 100;
        }
        return 100 - 100 / (1 + gain / loss);
    }
}
