package com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.type;

import com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.IndicatorDefinition;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.OutputDef;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.schema.ParamDef;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.spec.IndicatorSpec;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.candles.BearishEngulfingIndicator;
import org.ta4j.core.indicators.candles.BearishHaramiIndicator;
import org.ta4j.core.indicators.candles.BullishEngulfingIndicator;
import org.ta4j.core.indicators.candles.BullishHaramiIndicator;
import org.ta4j.core.indicators.candles.DojiIndicator;
import org.ta4j.core.indicators.candles.ThreeBlackCrowsIndicator;
import org.ta4j.core.indicators.candles.ThreeWhiteSoldiersIndicator;
import org.ta4j.core.num.Num;

import java.util.List;
import java.util.function.BiFunction;

import static com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.ComputationModel.COMPOSITE;
import static com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.ComputationModel.STATELESS;
import static com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.ComputationModel.WINDOW;
import static com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.DefinitionBuilder.define;
import static com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.IndicatorFamily.CANDLESTICK_PATTERN;
import static com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.type.DefinitionSupport.*;

/** Candlestick patterns (catalog section 3.10) as in ta4j 0.18. Value 1 = pattern at this bar, 0 = not. */
public final class CandlestickIndicators {

    public static final String DOJI = "DOJI";
    public static final String BULLISH_ENGULFING = "BULLISH_ENGULFING";
    public static final String BEARISH_ENGULFING = "BEARISH_ENGULFING";
    public static final String BULLISH_HARAMI = "BULLISH_HARAMI";
    public static final String BEARISH_HARAMI = "BEARISH_HARAMI";
    public static final String THREE_WHITE_SOLDIERS = "THREE_WHITE_SOLDIERS";
    public static final String THREE_BLACK_CROWS = "THREE_BLACK_CROWS";
    public static final String HAMMER = "HAMMER";
    public static final String HANGING_MAN = "HANGING_MAN";
    public static final String INVERTED_HAMMER = "INVERTED_HAMMER";
    public static final String SHOOTING_STAR = "SHOOTING_STAR";

    private static final String WICK_LOWER = "lowerWickRatio";
    private static final String WICK_UPPER = "upperWickRatio";
    private static final String TREND_PERIOD = "trendPeriod";

    private CandlestickIndicators() {
    }

    public static List<IndicatorDefinition> definitions() {
        return List.of(doji(),
                twoBar(BULLISH_ENGULFING, "Bullish engulfing", BullishEngulfingIndicator::new),
                twoBar(BEARISH_ENGULFING, "Bearish engulfing", BearishEngulfingIndicator::new),
                twoBar(BULLISH_HARAMI, "Bullish harami", BullishHaramiIndicator::new),
                twoBar(BEARISH_HARAMI, "Bearish harami", BearishHaramiIndicator::new),
                threeBar(THREE_WHITE_SOLDIERS, "Three white soldiers",
                        (series, s) -> new ThreeWhiteSoldiersIndicator(series, period(s),
                                series.numFactory().numOf(decimal(s, "factor")))),
                threeBar(THREE_BLACK_CROWS, "Three black crows",
                        (series, s) -> new ThreeBlackCrowsIndicator(series, period(s), decimal(s, "factor"))),
                wickPattern(HAMMER, "Hammer (long lower wick in a down trend)", true, false, 2, 1),
                wickPattern(HANGING_MAN, "Hanging man (long lower wick in an up trend)", true, true, 2, 1),
                wickPattern(INVERTED_HAMMER, "Inverted hammer (long upper wick in a down trend)", false, false, 1, 2),
                wickPattern(SHOOTING_STAR, "Shooting star (long upper wick in an up trend)", false, true, 1, 2));
    }

    /** Body smaller than bodyFactor * average body of the previous n bars. */
    static IndicatorDefinition doji() {
        return define(DOJI).description("Doji").family(CANDLESTICK_PATTERN).models(WINDOW)
                .params(period(10), ParamDef.decimal("bodyFactor", 0.03, 0.0001, 1, "share of the average body"))
                .outputs(OutputDef.value("1 = doji"))
                .lookback(s -> period(s)).warmup(s -> period(s) + 1)
                .build((c, s) -> single(wrap(c, s, VALUE,
                        new DojiIndicator(c.series(), period(s), decimal(s, "bodyFactor")))));
    }

    static IndicatorDefinition twoBar(String type, String description,
                                      java.util.function.Function<BarSeries, Indicator<Boolean>> factory) {
        return define(type).description(description).family(CANDLESTICK_PATTERN).models(STATELESS)
                .params().outputs(OutputDef.value("1 = pattern"))
                .lookback(s -> 1).warmup(s -> 2)
                .build((c, s) -> single(wrap(c, s, VALUE, factory.apply(c.series()))));
    }

    /** Three candles after an opposite one, each with a short shadow versus the n-bar average shadow. */
    static IndicatorDefinition threeBar(String type, String description,
                                        BiFunction<BarSeries, IndicatorSpec, Indicator<Boolean>> factory) {
        return define(type).description(description).family(CANDLESTICK_PATTERN).models(WINDOW)
                .params(period(3), ParamDef.decimal("factor", 0.3, 0.0001, 10, "share of the average shadow"))
                .outputs(OutputDef.value("1 = pattern"))
                .lookback(s -> period(s) + 2).warmup(s -> period(s) + 3)
                .build((c, s) -> single(wrap(c, s, VALUE, factory.apply(c.series(), s))));
    }

    /**
     * Hammer family (ta4j): wick/body ratios plus a trend filter: ADX(trendPeriod) > 25 and, on the previous bar,
     * -DI > +DI (down trend) or -DI < +DI (up trend). The ADX is a shared dependency, so the pattern is seedable.
     *
     * @param lowerWick true: long lower wick (hammer, hanging man); false: long upper wick
     * @param upTrend   trend the pattern needs
     */
    static IndicatorDefinition wickPattern(String type, String description, boolean lowerWick, boolean upTrend,
                                           double lowerRatio, double upperRatio) {
        return define(type).description(description).family(CANDLESTICK_PATTERN).models(STATELESS, COMPOSITE)
                .params(ParamDef.decimal(WICK_LOWER, lowerRatio, 0.01, 100, "lower wick / body"),
                        ParamDef.decimal(WICK_UPPER, upperRatio, 0.01, 100, "upper wick / body"),
                        ParamDef.period(TREND_PERIOD, 5, "ADX/DI period of the trend filter"))
                .outputs(OutputDef.value("1 = pattern"))
                .dependencies(s -> List.of(adx(s).output(AdxDefinition.OUT_ADX),
                        adx(s).output(AdxDefinition.OUT_PLUS_DI), adx(s).output(AdxDefinition.OUT_MINUS_DI)))
                .lookback(s -> 1).warmup(s -> 2)
                .build((c, s) -> {
                    BarSeries series = c.series();
                    double maxLower = decimal(s, WICK_LOWER);
                    double maxUpper = decimal(s, WICK_UPPER);
                    Indicator<Num> adx = c.source(adx(s).output(AdxDefinition.OUT_ADX));
                    Indicator<Num> plusDi = c.source(adx(s).output(AdxDefinition.OUT_PLUS_DI));
                    Indicator<Num> minusDi = c.source(adx(s).output(AdxDefinition.OUT_MINUS_DI));
                    return single(lambda(c, s, VALUE, (i, self) -> {
                        double open = open(series, i);
                        double close = close(series, i);
                        double body = Math.abs(close - open);
                        double bottomWick = Math.min(open, close) - low(series, i);
                        double upperWick = high(series, i) - Math.max(open, close);
                        double bottomRatio = body == 0 ? Double.NaN : bottomWick / body;
                        double upperRatio2 = body == 0 ? Double.NaN : upperWick / body;
                        boolean shape = lowerWick
                                ? bottomRatio > maxLower && upperRatio2 <= maxUpper
                                : upperRatio2 > maxUpper && bottomRatio <= maxLower;
                        boolean strong = v(adx, i) > 25;
                        boolean trend = upTrend ? v(minusDi, i - 1) < v(plusDi, i - 1)
                                : v(minusDi, i - 1) > v(plusDi, i - 1);
                        return shape && strong && trend ? 1 : 0;
                    }));
                });
    }

    private static IndicatorSpec adx(IndicatorSpec s) {
        return IndicatorSpec.of(AdxDefinition.TYPE).with(AdxDefinition.DI_LENGTH, param(s, TREND_PERIOD))
                .with(AdxDefinition.ADX_SMOOTHING, param(s, TREND_PERIOD));
    }
}
