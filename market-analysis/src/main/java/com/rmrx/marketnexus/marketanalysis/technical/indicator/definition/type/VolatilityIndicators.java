package com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.type;

import com.rmrx.marketnexus.marketanalysis.technical.indicator.block.Seed;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.block.TrueRangeBlock;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.block.WindowExtremeBlock;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.IndicatorDefinition;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.OutputDef;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.schema.ParamDef;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.spec.IndicatorSpec;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.spec.PriceField;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.UlcerIndexIndicator;
import org.ta4j.core.indicators.donchian.DonchianChannelLowerIndicator;
import org.ta4j.core.indicators.donchian.DonchianChannelMiddleIndicator;
import org.ta4j.core.indicators.donchian.DonchianChannelUpperIndicator;
import org.ta4j.core.indicators.statistics.StandardDeviationIndicator;
import org.ta4j.core.num.Num;

import java.util.List;
import java.util.Map;

import static com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.ComputationModel.COMPOSITE;
import static com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.ComputationModel.RECURSIVE;
import static com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.ComputationModel.WINDOW;
import static com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.DefinitionBuilder.define;
import static com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.IndicatorFamily.VOLATILITY;
import static com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.type.DefinitionSupport.*;

/** Volatility indicators besides ATR and Bollinger Bands (catalog section 3.5). Formulas follow ta4j 0.18. */
public final class VolatilityIndicators {

    public static final String STDDEV = "STDDEV";
    public static final String KELTNER = "KELTNER";
    public static final String DONCHIAN = "DONCHIAN";
    public static final String ULCER = "ULCER";
    public static final String CHANDELIER = "CHANDELIER";
    public static final String CHOP = "CHOP";

    private VolatilityIndicators() {
    }

    public static List<IndicatorDefinition> definitions() {
        return List.of(stdDev(), keltner(), donchian(), ulcer(), chandelier(), chop());
    }

    /** Population standard deviation. */
    static IndicatorDefinition stdDev() {
        return define(STDDEV).description("Standard deviation").family(VOLATILITY).models(WINDOW)
                .params(period(20), source(PriceField.CLOSE)).outputs(OutputDef.value("standard deviation"))
                .lookback(s -> period(s) - 1).warmup(s -> period(s))
                .build((c, s) -> single(wrap(c, s, VALUE, new StandardDeviationIndicator(source(c, s), period(s)))));
    }

    /** Keltner channel (ta4j): middle = EMA of typical price, bands = middle +/- multiplier * ATR. */
    static IndicatorDefinition keltner() {
        return define(KELTNER).description("Keltner channel").family(VOLATILITY).models(RECURSIVE, COMPOSITE)
                .params(ParamDef.period("emaPeriod", 20, "EMA of typical price"),
                        ParamDef.period("atrPeriod", 10, "ATR period"),
                        ParamDef.decimal("multiplier", 2, 0.1, 10, "ATR multiplier"))
                .outputs(OutputDef.publicOutput("upper", ""), OutputDef.publicOutput("middle", ""),
                        OutputDef.publicOutput("lower", ""))
                .dependencies(s -> List.of(ref(middleEma(s)), ref(atr(param(s, "atrPeriod")))))
                .lookback(s -> 0).warmup(s -> 0)
                .build((c, s) -> {
                    double m = decimal(s, "multiplier");
                    Indicator<Num> middle = c.source(ref(middleEma(s)));
                    Indicator<Num> atr = c.source(ref(atr(param(s, "atrPeriod"))));
                    return Map.of(
                            "upper", formula(c, s, "upper", x -> x[0] + m * x[1], middle, atr),
                            "middle", formula(c, s, "middle", x -> x[0], middle),
                            "lower", formula(c, s, "lower", x -> x[0] - m * x[1], middle, atr));
                });
    }

    private static IndicatorSpec middleEma(IndicatorSpec s) {
        return ema(param(s, "emaPeriod"), PriceField.HLC3);
    }

    static IndicatorDefinition donchian() {
        return define(DONCHIAN).description("Donchian channel").family(VOLATILITY).models(WINDOW)
                .params(period(20))
                .outputs(OutputDef.publicOutput("upper", "highest high"), OutputDef.publicOutput("middle", ""),
                        OutputDef.publicOutput("lower", "lowest low"))
                .lookback(s -> period(s) - 1).warmup(s -> period(s))
                .build((c, s) -> Map.of(
                        "upper", wrap(c, s, "upper", new DonchianChannelUpperIndicator(c.series(), period(s))),
                        "middle", wrap(c, s, "middle", new DonchianChannelMiddleIndicator(c.series(), period(s))),
                        "lower", wrap(c, s, "lower", new DonchianChannelLowerIndicator(c.series(), period(s)))));
    }

    static IndicatorDefinition ulcer() {
        return define(ULCER).description("Ulcer index").family(VOLATILITY).models(WINDOW)
                .params(period(14), source(PriceField.CLOSE)).outputs(OutputDef.value("ulcer index"))
                .lookback(s -> period(s) - 1).warmup(s -> period(s))
                .build((c, s) -> single(wrap(c, s, VALUE, new UlcerIndexIndicator(source(c, s), period(s)))));
    }

    /** Chandelier exit: highest high - k * ATR (long), lowest low + k * ATR (short); ATR shared. */
    static IndicatorDefinition chandelier() {
        return define(CHANDELIER).description("Chandelier exit").family(VOLATILITY).models(WINDOW, COMPOSITE)
                .params(period(22), ParamDef.decimal("multiplier", 3, 0.1, 20, "ATR multiplier"))
                .outputs(OutputDef.publicOutput("long", "stop for longs"), OutputDef.publicOutput("short", "stop for shorts"))
                .dependencies(s -> List.of(ref(atr(period(s)))))
                .lookback(s -> period(s) - 1).warmup(s -> period(s))
                .build((c, s) -> {
                    int n = period(s);
                    double k = decimal(s, "multiplier");
                    Indicator<Num> atr = c.source(ref(atr(n)));
                    Indicator<Num> high = WindowExtremeBlock.highest(price(c, PriceField.HIGH), n, Seed.none());
                    Indicator<Num> low = WindowExtremeBlock.lowest(price(c, PriceField.LOW), n, Seed.none());
                    return Map.of(
                            "long", formula(c, s, "long", x -> x[0] - x[1] * k, high, atr),
                            "short", formula(c, s, "short", x -> x[0] + x[1] * k, low, atr));
                });
    }

    /** Choppiness index: scale * log10(sum TR(n) / (HH - LL)) / log10(n). */
    static IndicatorDefinition chop() {
        return define(CHOP).description("Choppiness index").family(VOLATILITY).models(WINDOW)
                .params(period(14), ParamDef.integer("scale", 100, 1, 1000, "upper end of the scale"))
                .outputs(OutputDef.value("choppiness"))
                .validate(s -> period(s) >= 2, "period must be at least 2")
                .lookback(s -> period(s)).warmup(s -> period(s) + 1)
                .build((c, s) -> {
                    int n = period(s);
                    double scale = param(s, "scale");
                    double log10n = Math.log10(n);
                    Indicator<Num> tr = new TrueRangeBlock(c.series(), Seed.none());
                    Indicator<Num> high = WindowExtremeBlock.highest(price(c, PriceField.HIGH), n, Seed.none());
                    Indicator<Num> low = WindowExtremeBlock.lowest(price(c, PriceField.LOW), n, Seed.none());
                    return single(lambda(c, s, VALUE, (i, self) -> {
                        double sum = 0;
                        for (int k = 0; k < n; k++) {
                            sum += v(tr, i - k);
                        }
                        return scale * Math.log10(sum / (v(high, i) - v(low, i))) / log10n;
                    }));
                });
    }
}
