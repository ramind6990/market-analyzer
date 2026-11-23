package com.rmrx.marketanalyzer.marketanalysis.technical.indicator.definition.type;

import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.block.Seed;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.block.SmaBlock;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.block.WindowExtremeBlock;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.definition.IndicatorDefinition;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.definition.OutputDef;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.schema.ParamDef;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.spec.IndicatorSpec;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.spec.PriceField;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AccelerationDecelerationIndicator;
import org.ta4j.core.indicators.AwesomeOscillatorIndicator;
import org.ta4j.core.indicators.CCIIndicator;
import org.ta4j.core.indicators.CMOIndicator;
import org.ta4j.core.indicators.CoppockCurveIndicator;
import org.ta4j.core.indicators.KSTIndicator;
import org.ta4j.core.indicators.ROCIndicator;
import org.ta4j.core.indicators.WilliamsRIndicator;
import org.ta4j.core.num.Num;

import java.util.List;
import java.util.Map;

import static com.rmrx.marketanalyzer.marketanalysis.technical.indicator.definition.ComputationModel.COMPOSITE;
import static com.rmrx.marketanalyzer.marketanalysis.technical.indicator.definition.ComputationModel.RECURSIVE;
import static com.rmrx.marketanalyzer.marketanalysis.technical.indicator.definition.ComputationModel.WINDOW;
import static com.rmrx.marketanalyzer.marketanalysis.technical.indicator.definition.DefinitionBuilder.define;
import static com.rmrx.marketanalyzer.marketanalysis.technical.indicator.definition.IndicatorFamily.MOMENTUM;
import static com.rmrx.marketanalyzer.marketanalysis.technical.indicator.definition.type.DefinitionSupport.*;

/** Momentum and oscillators besides RSI and Stochastic (catalog section 3.4). Formulas follow ta4j 0.18. */
public final class MomentumIndicators {

    public static final String STOCH_RSI = "STOCH_RSI";
    public static final String WILLIAMS_R = "WILLIAMS_R";
    public static final String CCI = "CCI";
    public static final String ROC = "ROC";
    public static final String CMO = "CMO";
    public static final String AO = "AO";
    public static final String KST = "KST";
    public static final String COPPOCK = "COPPOCK";
    public static final String FISHER = "FISHER";
    public static final String SQUEEZE = "SQUEEZE";

    private MomentumIndicators() {
    }

    public static List<IndicatorDefinition> definitions() {
        return List.of(stochRsi(), williamsR(), cci(), roc(), cmo(), awesome(), kst(), coppock(), fisher(), squeeze());
    }

    /**
     * Stochastic of RSI values: (RSI - min) / (max - min) over n bars, 0..1 like ta4j, plus %K and %D smoothing.
     * The RSI is a shared dependency. A flat RSI window gives 0.5 (ta4j: NaN).
     */
    static IndicatorDefinition stochRsi() {
        return define(STOCH_RSI).description("Stochastic RSI").family(MOMENTUM).models(WINDOW, COMPOSITE)
                .params(ParamDef.period("rsiPeriod", 14, "RSI period"), ParamDef.period("stochPeriod", 14,
                                "min/max window of the RSI"),
                        ParamDef.period("kSmooth", 3, "SMA of the raw value"), ParamDef.period("dPeriod", 3, "SMA of %K"),
                        source(PriceField.CLOSE))
                .outputs(OutputDef.value("raw stochastic RSI 0..1"), OutputDef.publicOutput("k", "SMA of raw"),
                        OutputDef.publicOutput("d", "SMA of k"))
                .dependencies(s -> List.of(ref(rsi(s))))
                .lookback(s -> Math.max(param(s, "stochPeriod"), Math.max(param(s, "kSmooth"), param(s, "dPeriod"))) - 1)
                .warmup(s -> param(s, "stochPeriod") + param(s, "kSmooth") + param(s, "dPeriod"))
                .build((c, s) -> {
                    int n = param(s, "stochPeriod");
                    Indicator<Num> rsi = c.source(ref(rsi(s)));
                    Indicator<Num> max = WindowExtremeBlock.highest(rsi, n, Seed.none());
                    Indicator<Num> min = WindowExtremeBlock.lowest(rsi, n, Seed.none());
                    Indicator<Num> raw = formula(c, s, VALUE, x -> x[1] == x[2] ? 0.5 : (x[0] - x[2]) / (x[1] - x[2]),
                            rsi, max, min);
                    Indicator<Num> k = new SmaBlock(raw, param(s, "kSmooth"), c.seed(s, "k"));
                    Indicator<Num> d = new SmaBlock(k, param(s, "dPeriod"), c.seed(s, "d"));
                    return Map.of(VALUE, raw, "k", k, "d", d);
                });
    }

    private static IndicatorSpec rsi(IndicatorSpec s) {
        return IndicatorSpec.of(RsiDefinition.TYPE).with(RsiDefinition.PERIOD, param(s, "rsiPeriod"))
                .with(RsiDefinition.SOURCE, s.sourceParam(SOURCE));
    }

    static IndicatorDefinition williamsR() {
        return define(WILLIAMS_R).description("Williams %R").family(MOMENTUM).models(WINDOW)
                .params(period(14)).outputs(OutputDef.value("%R -100..0"))
                .lookback(s -> period(s) - 1).warmup(s -> period(s))
                .build((c, s) -> single(wrap(c, s, VALUE, new WilliamsRIndicator(c.series(), period(s)))));
    }

    static IndicatorDefinition cci() {
        return define(CCI).description("Commodity channel index").family(MOMENTUM).models(WINDOW)
                .params(period(20)).outputs(OutputDef.value("CCI"))
                .lookback(s -> period(s) - 1).warmup(s -> period(s))
                .build((c, s) -> single(wrap(c, s, VALUE, new CCIIndicator(c.series(), period(s)))));
    }

    static IndicatorDefinition roc() {
        return define(ROC).description("Rate of change in percent").family(MOMENTUM).models(WINDOW)
                .params(period(10), source(PriceField.CLOSE)).outputs(OutputDef.value("percent change over n bars"))
                .lookback(s -> period(s)).warmup(s -> period(s) + 1)
                .build((c, s) -> single(wrap(c, s, VALUE, new ROCIndicator(source(c, s), period(s)))));
    }

    static IndicatorDefinition cmo() {
        return define(CMO).description("Chande momentum oscillator").family(MOMENTUM).models(WINDOW)
                .params(period(14), source(PriceField.CLOSE)).outputs(OutputDef.value("CMO -100..100"))
                .lookback(s -> period(s)).warmup(s -> period(s) + 1)
                .build((c, s) -> single(wrap(c, s, VALUE, new CMOIndicator(source(c, s), period(s)))));
    }

    /** Awesome oscillator SMA(fast) - SMA(slow) of HL2, and accelerator AO - SMA(AO, fast). */
    static IndicatorDefinition awesome() {
        return define(AO).description("Awesome and accelerator oscillator").family(MOMENTUM).models(WINDOW)
                .params(ParamDef.period("fast", 5, "fast SMA"), ParamDef.period("slow", 34, "slow SMA"))
                .outputs(OutputDef.value("awesome oscillator"), OutputDef.publicOutput("accelerator", "AO - SMA(AO)"))
                .validate(s -> param(s, "fast") < param(s, "slow"), "fast must be smaller than slow")
                .lookback(s -> param(s, "slow") + param(s, "fast") - 2).warmup(s -> param(s, "slow") + param(s, "fast"))
                .build((c, s) -> Map.of(
                        VALUE, wrap(c, s, VALUE, new AwesomeOscillatorIndicator(price(c, PriceField.HL2),
                                param(s, "fast"), param(s, "slow"))),
                        "accelerator", wrap(c, s, "accelerator", new AccelerationDecelerationIndicator(c.series(),
                                param(s, "fast"), param(s, "slow")))));
    }

    /** Know sure thing: weighted sum of four smoothed ROCs, plus an SMA signal line. */
    static IndicatorDefinition kst() {
        return define(KST).description("Know sure thing").family(MOMENTUM).models(WINDOW, COMPOSITE)
                .params(ParamDef.period("roc1", 10, ""), ParamDef.period("sma1", 10, ""),
                        ParamDef.period("roc2", 15, ""), ParamDef.period("sma2", 10, ""),
                        ParamDef.period("roc3", 20, ""), ParamDef.period("sma3", 10, ""),
                        ParamDef.period("roc4", 30, ""), ParamDef.period("sma4", 15, ""),
                        ParamDef.period("signal", 9, "SMA of KST"), source(PriceField.CLOSE))
                .outputs(OutputDef.value("KST"), OutputDef.publicOutput("signal", "SMA of KST"))
                .lookback(s -> Math.max(kstWindow(s), param(s, "signal") - 1))
                .warmup(s -> kstWindow(s) + param(s, "signal"))
                .build((c, s) -> {
                    Indicator<Num> kst = wrap(c, s, VALUE, new KSTIndicator(source(c, s),
                            param(s, "sma1"), param(s, "roc1"), param(s, "sma2"), param(s, "roc2"),
                            param(s, "sma3"), param(s, "roc3"), param(s, "sma4"), param(s, "roc4")));
                    return Map.of(VALUE, kst, "signal", new SmaBlock(kst, param(s, "signal"), c.seed(s, "signal")));
                });
    }

    private static int kstWindow(IndicatorSpec s) {
        int max = 0;
        for (int k = 1; k <= 4; k++) {
            max = Math.max(max, param(s, "roc" + k) + param(s, "sma" + k) - 1);
        }
        return max;
    }

    /** Coppock curve: WMA of ROC(long) + ROC(short). */
    static IndicatorDefinition coppock() {
        return define(COPPOCK).description("Coppock curve").family(MOMENTUM).models(WINDOW, COMPOSITE)
                .params(ParamDef.period("longRoc", 14, ""), ParamDef.period("shortRoc", 11, ""),
                        ParamDef.period("wma", 10, ""), source(PriceField.CLOSE))
                .outputs(OutputDef.value("Coppock curve"))
                .lookback(s -> Math.max(param(s, "longRoc"), param(s, "shortRoc")) + param(s, "wma") - 1)
                .warmup(s -> Math.max(param(s, "longRoc"), param(s, "shortRoc")) + param(s, "wma"))
                .build((c, s) -> single(wrap(c, s, VALUE, new CoppockCurveIndicator(source(c, s),
                        param(s, "longRoc"), param(s, "shortRoc"), param(s, "wma")))));
    }

    /**
     * Ehlers Fisher transform as in ta4j (HL2, alpha 0.33, beta 0.67, gamma = delta = 0.5): an internal smoothed
     * position within the n-bar high/low range, then the Fisher transform, smoothed. Trigger = previous value.
     * Deviation from ta4j: a flat range counts as the middle (ta4j: NaN for ever after).
     */
    static IndicatorDefinition fisher() {
        return define(FISHER).description("Fisher transform").family(MOMENTUM).models(RECURSIVE, WINDOW)
                .params(period(10))
                .outputs(OutputDef.value("Fisher transform"), OutputDef.publicOutput("trigger", "previous value"),
                        OutputDef.internal("intermediate", "smoothed normalized price"))
                .lookback(s -> Math.max(1, period(s) - 1)).warmup(s -> period(s) + 20)
                .build((c, s) -> {
                    BarSeries series = c.series();
                    int begin = series.getBeginIndex();
                    int n = period(s);
                    Indicator<Num> ref = price(c, PriceField.HL2);
                    Indicator<Num> high = WindowExtremeBlock.highest(price(c, PriceField.HIGH), n, Seed.none());
                    Indicator<Num> low = WindowExtremeBlock.lowest(price(c, PriceField.LOW), n, Seed.none());
                    Indicator<Num> intermediate = lambda(c, s, "intermediate", (i, self) -> {
                        if (i <= begin) {
                            return 0;
                        }
                        double minL = v(low, i);
                        double range = v(high, i) - minL;
                        double term1 = range == 0 ? 0 : (v(ref, i) - minL) / range - 0.5;
                        return (0.33 * 2 * term1 + 0.67 * self.applyAsDouble(i - 1)) / 1.0;
                    });
                    Indicator<Num> fisher = lambda(c, s, VALUE, (i, self) -> {
                        if (i <= begin) {
                            return 0;
                        }
                        double value = Math.max(-0.999, Math.min(0.999, v(intermediate, i)));
                        return 0.5 * Math.log((1 + value) / (1 - value)) + 0.5 * self.applyAsDouble(i - 1);
                    });
                    Indicator<Num> trigger = lambda(c, s, "trigger", (i, self) -> i <= begin ? 0 : v(fisher, i - 1));
                    return Map.of(VALUE, fisher, "trigger", trigger, "intermediate", intermediate);
                });
    }

    /**
     * Squeeze (ta4j SqueezePro): Bollinger Bands inside Keltner Channels (EMA of typical price +/- factor * ATR).
     * value = 1 when squeezed against any of the three Keltner widths; level = 3 (tight, 1.0 ATR), 2 (1.5), 1 (2.0),
     * 0 none. The momentum part of the classic squeeze is not in ta4j and not offered.
     */
    static IndicatorDefinition squeeze() {
        return define(SQUEEZE).description("Bollinger/Keltner squeeze").family(MOMENTUM).models(COMPOSITE, WINDOW)
                .params(period(20), ParamDef.decimal("bbMultiplier", 2, 0.1, 10, "Bollinger width"),
                        ParamDef.decimal("kcHigh", 1, 0.1, 10, "tight Keltner factor"),
                        ParamDef.decimal("kcMid", 1.5, 0.1, 10, "medium Keltner factor"),
                        ParamDef.decimal("kcLow", 2, 0.1, 10, "wide Keltner factor"))
                .outputs(OutputDef.value("1 = squeeze, 0 = none"), OutputDef.publicOutput("level", "0..3"))
                .dependencies(s -> List.of(bands(s).output(BollingerDefinition.OUT_UPPER),
                        bands(s).output(BollingerDefinition.OUT_LOWER), ref(keltnerEma(s)), ref(atr(period(s)))))
                .lookback(s -> period(s)).warmup(s -> 0)
                .build((c, s) -> {
                    int begin = c.series().getBeginIndex();
                    int n = period(s);
                    double[] factors = {decimal(s, "kcHigh"), decimal(s, "kcMid"), decimal(s, "kcLow")};
                    Indicator<Num> upper = c.source(bands(s).output(BollingerDefinition.OUT_UPPER));
                    Indicator<Num> lower = c.source(bands(s).output(BollingerDefinition.OUT_LOWER));
                    Indicator<Num> middle = c.source(ref(keltnerEma(s)));
                    Indicator<Num> atr = c.source(ref(atr(n)));
                    Indicator<Num> level = formula(c, s, "level", x -> {
                        for (int f = 0; f < factors.length; f++) {
                            if (x[1] > x[2] - factors[f] * x[3] && x[0] < x[2] + factors[f] * x[3]) {
                                return 3 - f;
                            }
                        }
                        return 0;
                    }, upper, lower, middle, atr);
                    Indicator<Num> value = lambda(c, s, VALUE, (i, self) -> i - begin < n ? 0 : v(level, i) > 0 ? 1 : 0);
                    return Map.of(VALUE, value, "level", level);
                });
    }

    private static IndicatorSpec bands(IndicatorSpec s) {
        return IndicatorSpec.of(BollingerDefinition.TYPE).with(BollingerDefinition.PERIOD, period(s))
                .with(BollingerDefinition.MULTIPLIER, s.decimalParam("bbMultiplier"))
                .with(BollingerDefinition.SOURCE, PriceField.CLOSE);
    }

    private static IndicatorSpec keltnerEma(IndicatorSpec s) {
        return ema(period(s), PriceField.HLC3);
    }
}
