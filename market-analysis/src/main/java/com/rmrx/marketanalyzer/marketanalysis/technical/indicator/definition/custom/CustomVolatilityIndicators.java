package com.rmrx.marketanalyzer.marketanalysis.technical.indicator.definition.custom;

import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.block.ExponentialBlock;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.definition.IndicatorDefinition;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.definition.OutputDef;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.definition.type.SmaDefinition;
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
import static com.rmrx.marketanalyzer.marketanalysis.technical.indicator.definition.IndicatorFamily.VOLATILITY;
import static com.rmrx.marketanalyzer.marketanalysis.technical.indicator.definition.type.DefinitionSupport.*;

/** Volatility indicators without a ta4j indicator. */
public final class CustomVolatilityIndicators {

    public static final String NATR = "NATR";
    public static final String MA_ENVELOPE = "MA_ENVELOPE";
    public static final String CHAIKIN_VOL = "CHAIKIN_VOL";
    public static final String HV = "HV";

    /** Crypto trades around the clock: 365 days of 24 hours. */
    private static final double SECONDS_PER_YEAR = 365.0 * 24 * 3600;

    private CustomVolatilityIndicators() {
    }

    public static List<IndicatorDefinition> definitions() {
        return List.of(natr(), maEnvelope(), chaikinVolatility(), historicalVolatility());
    }

    /**
     * Historical volatility: 100 * sample standard deviation (n - 1) of the last n log returns * sqrt(bars per year),
     * bars per year from the bar duration (365 x 24 h, crypto). Non-positive prices count as a 0 return.
     */
    static IndicatorDefinition historicalVolatility() {
        return define(HV).description("Historical volatility (annualized)").family(VOLATILITY).models(WINDOW)
                .params(ParamDef.integer(PERIOD, 20, 2, 1000, "log returns"), source(PriceField.CLOSE))
                .outputs(OutputDef.value("annualized volatility in %"))
                .lookback(s -> period(s)).warmup(s -> period(s) + 1)
                .build((c, s) -> {
                    BarSeries series = c.series();
                    Indicator<Num> source = source(c, s);
                    int begin = series.getBeginIndex();
                    int n = period(s);
                    return single(lambda(c, s, VALUE, (i, self) -> {
                        if (i - n < begin) {
                            return Double.NaN;
                        }
                        double sum = 0;
                        double sumSquares = 0;
                        for (int j = i - n + 1; j <= i; j++) {
                            double now = v(source, j);
                            double before = v(source, j - 1);
                            double r = now > 0 && before > 0 ? Math.log(now / before) : 0;
                            sum += r;
                            sumSquares += r * r;
                        }
                        double variance = Math.max(0, (sumSquares - sum * sum / n) / (n - 1));
                        double barSeconds = series.getBar(i).getTimePeriod().toSeconds();
                        return 100 * Math.sqrt(variance) * Math.sqrt(SECONDS_PER_YEAR / barSeconds);
                    }));
                });
    }

    /**
     * Chaikin volatility (Chaikin; TradingView / MetaStock definition): 100 * (E - E n bars ago) / E n bars ago with
     * E = EMA(high - low). The EMA is recursive state (INTERNAL output) and is also read {@code roc} bars back.
     * 0 when the old average is 0 (flat filler candles only).
     */
    static IndicatorDefinition chaikinVolatility() {
        return define(CHAIKIN_VOL).description("Chaikin volatility").family(VOLATILITY).models(RECURSIVE, WINDOW)
                .params(period(10), ParamDef.period("roc", 10, "rate-of-change distance"))
                .outputs(OutputDef.value("% change of the range EMA"),
                        OutputDef.internal("rangeEma", "EMA of high - low"))
                .lookback(s -> param(s, "roc")).warmup(s -> EMA_FACTOR * period(s) + param(s, "roc"))
                .build((c, s) -> {
                    BarSeries series = c.series();
                    int begin = series.getBeginIndex();
                    int roc = param(s, "roc");
                    Indicator<Num> range = helper(c, (i, self) -> high(series, i) - low(series, i));
                    Indicator<Num> rangeEma = ExponentialBlock.ema(range, period(s), c.seed(s, "rangeEma"));
                    Indicator<Num> value = lambda(c, s, VALUE, (i, self) -> {
                        if (i - roc < begin) {
                            return Double.NaN;
                        }
                        double old = v(rangeEma, i - roc);
                        return old == 0 ? 0 : 100 * (v(rangeEma, i) - old) / old;
                    });
                    return Map.of(VALUE, value, "rangeEma", rangeEma);
                });
    }

    /** Normalized ATR (TA-Lib {@code NATR}): 100 * ATR / close; ATR is the shared Wilder ATR node. */
    static IndicatorDefinition natr() {
        return define(NATR).description("Normalized average true range").family(VOLATILITY)
                .models(STATELESS, COMPOSITE).params(period(14)).outputs(OutputDef.value("ATR in % of close"))
                .dependencies(s -> List.of(ref(atr(period(s)))))
                .lookback(s -> 0).warmup(s -> 0)
                .build((c, s) -> single(formula(c, s, VALUE, x -> x[1] == 0 ? Double.NaN : 100 * x[0] / x[1],
                        c.source(ref(atr(period(s)))), price(c, PriceField.CLOSE))));
    }

    /** Moving average envelope: SMA * (1 +- percent / 100); the SMA is a shared node. */
    static IndicatorDefinition maEnvelope() {
        return define(MA_ENVELOPE).description("Moving average envelope").family(VOLATILITY)
                .models(STATELESS, COMPOSITE)
                .params(period(20), ParamDef.decimal("percent", 2.5, 0.01, 100, "band distance in % of the SMA"),
                        source(PriceField.CLOSE))
                .outputs(OutputDef.publicOutput("upper", "SMA * (1 + p/100)"), OutputDef.publicOutput("middle", "SMA"),
                        OutputDef.publicOutput("lower", "SMA * (1 - p/100)"))
                .dependencies(s -> List.of(ref(envelopeSma(s))))
                .lookback(s -> 0).warmup(s -> 0)
                .build((c, s) -> {
                    Indicator<Num> sma = c.source(ref(envelopeSma(s)));
                    double factor = decimal(s, "percent") / 100;
                    return Map.of(
                            "upper", formula(c, s, "upper", x -> x[0] * (1 + factor), sma),
                            "middle", formula(c, s, "middle", x -> x[0], sma),
                            "lower", formula(c, s, "lower", x -> x[0] * (1 - factor), sma));
                });
    }

    private static IndicatorSpec envelopeSma(IndicatorSpec spec) {
        return SmaDefinition.spec(period(spec), spec.sourceParam(SOURCE));
    }
}
