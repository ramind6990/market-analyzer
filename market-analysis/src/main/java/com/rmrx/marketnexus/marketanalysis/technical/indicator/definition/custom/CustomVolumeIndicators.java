package com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.custom;

import com.rmrx.marketnexus.marketanalysis.technical.indicator.block.ExponentialBlock;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.IndicatorDefinition;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.OutputDef;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.type.SmaDefinition;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.schema.ParamDef;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.spec.IndicatorSpec;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.spec.PriceField;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;

import java.util.List;
import java.util.Map;

import static com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.ComputationModel.COMPOSITE;
import static com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.ComputationModel.CUMULATIVE;
import static com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.ComputationModel.RECURSIVE;
import static com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.ComputationModel.STATELESS;
import static com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.ComputationModel.WINDOW;
import static com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.DefinitionBuilder.define;
import static com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.IndicatorFamily.VOLUME;
import static com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.type.DefinitionSupport.*;

/** Volume indicators without a ta4j indicator. */
public final class CustomVolumeIndicators {

    public static final String RVOL = "RVOL";
    public static final String FORCE_INDEX = "FORCE_INDEX";
    public static final String PVT = "PVT";
    public static final String KLINGER = "KLINGER";
    public static final String EOM = "EOM";

    private CustomVolumeIndicators() {
    }

    public static List<IndicatorDefinition> definitions() {
        return List.of(relativeVolume(), forceIndex(), priceVolumeTrend(), klinger(), easeOfMovement());
    }

    /**
     * Ease of movement (Arms; TradingView {@code ta.eom}, pandas-ta {@code eom}): SMA over n of
     * divisor * (HL2 - previous HL2) * (high - low) / volume. A bar without volume or range contributes 0
     * (filler candles). pandas-ta's default divisor is 1e8, TradingView's 1e4.
     */
    static IndicatorDefinition easeOfMovement() {
        return define(EOM).description("Ease of movement").family(VOLUME).models(WINDOW)
                .params(period(14), ParamDef.decimal("divisor", 10000, 1, 1e12, "volume scale"))
                .outputs(OutputDef.value("SMA of the raw ease of movement"))
                .lookback(s -> period(s)).warmup(s -> period(s) + 1)
                .build((c, s) -> {
                    BarSeries series = c.series();
                    int begin = series.getBeginIndex();
                    int n = period(s);
                    double divisor = decimal(s, "divisor");
                    return single(lambda(c, s, VALUE, (i, self) -> {
                        if (i - n < begin) {
                            return Double.NaN;
                        }
                        double sum = 0;
                        for (int j = i - n + 1; j <= i; j++) {
                            double volume = volume(series, j);
                            if (volume == 0) {
                                continue;
                            }
                            double distance = (high(series, j) + low(series, j)) / 2
                                    - (high(series, j - 1) + low(series, j - 1)) / 2;
                            sum += divisor * distance * (high(series, j) - low(series, j)) / volume;
                        }
                        return sum / n;
                    }));
                });
    }

    /**
     * Klinger volume oscillator, simplified variant of pandas-ta {@code kvo} / TradingView: signed volume =
     * volume * sign(HLC3 - previous HLC3) (0 on the first bar and on unchanged HLC3); value = EMA(fast) - EMA(slow) of
     * the signed volume; signal = EMA of the value. Klinger's original trend/cumulative-measurement form is not used.
     * The two volume EMAs are recursive state (INTERNAL outputs).
     */
    static IndicatorDefinition klinger() {
        return define(KLINGER).description("Klinger volume oscillator").family(VOLUME).models(RECURSIVE)
                .params(ParamDef.period("fast", 34, "fast EMA period"), ParamDef.period("slow", 55, "slow EMA period"),
                        ParamDef.period("signal", 13, "EMA period of the signal line"))
                .outputs(OutputDef.value("EMA(fast) - EMA(slow) of signed volume"),
                        OutputDef.publicOutput("signal", "EMA of the value"),
                        OutputDef.internal("fastEma", "fast EMA of signed volume"),
                        OutputDef.internal("slowEma", "slow EMA of signed volume"))
                .validate(s -> param(s, "fast") < param(s, "slow"), "fast must be smaller than slow")
                .lookback(s -> 1).warmup(s -> EMA_FACTOR * (param(s, "slow") + param(s, "signal")) + 1)
                .build((c, s) -> {
                    BarSeries series = c.series();
                    int begin = series.getBeginIndex();
                    Indicator<Num> signed = helper(c, (i, self) -> {
                        if (i <= begin) {
                            return 0;
                        }
                        double change = hlc3(series, i) - hlc3(series, i - 1);
                        return Math.signum(change) * volume(series, i);
                    });
                    Indicator<Num> fast = ExponentialBlock.ema(signed, param(s, "fast"), c.seed(s, "fastEma"));
                    Indicator<Num> slow = ExponentialBlock.ema(signed, param(s, "slow"), c.seed(s, "slowEma"));
                    Indicator<Num> value = formula(c, s, VALUE, x -> x[0] - x[1], fast, slow);
                    return Map.of(VALUE, value,
                            "signal", ExponentialBlock.ema(value, param(s, "signal"), c.seed(s, "signal")),
                            "fastEma", fast, "slowEma", slow);
                });
    }

    private static double hlc3(BarSeries series, int index) {
        return (high(series, index) + low(series, index) + close(series, index)) / 3;
    }

    /**
     * Relative volume: volume / SMA(volume) (the volume SMA is a shared node). 0 when the average is 0 (only filler
     * candles in the window). Not the time-of-day RVOL, which needs same-hour history.
     */
    static IndicatorDefinition relativeVolume() {
        return define(RVOL).description("Relative volume").family(VOLUME).models(STATELESS, COMPOSITE)
                .params(period(20)).outputs(OutputDef.value("volume / average volume"))
                .dependencies(s -> List.of(ref(volumeSma(s))))
                .lookback(s -> 0).warmup(s -> 0)
                .build((c, s) -> single(formula(c, s, VALUE, x -> x[1] == 0 ? 0 : x[0] / x[1],
                        price(c, PriceField.VOLUME), c.source(ref(volumeSma(s))))));
    }

    private static IndicatorSpec volumeSma(IndicatorSpec spec) {
        return SmaDefinition.spec(period(spec), PriceField.VOLUME);
    }

    /** Elder's force index (pandas-ta {@code efi}): EMA of (close - previous close) * volume. */
    static IndicatorDefinition forceIndex() {
        return define(FORCE_INDEX).description("Force index").family(VOLUME).models(RECURSIVE)
                .params(period(13)).outputs(OutputDef.value("EMA of price change * volume"))
                .lookback(s -> 1).warmup(s -> EMA_FACTOR * period(s) + 1)
                .build((c, s) -> {
                    BarSeries series = c.series();
                    int begin = series.getBeginIndex();
                    Indicator<Num> force = helper(c, (i, self) -> i <= begin ? 0
                            : (close(series, i) - close(series, i - 1)) * volume(series, i));
                    return single(ExponentialBlock.ema(force, period(s), c.seed(s, VALUE)));
                });
    }

    /**
     * Price volume trend (pandas-ta {@code pvt}): running total of (close - previous close) / previous close * volume,
     * anchored at 0 on the first bar computed without a seed (like OBV and A/D).
     */
    static IndicatorDefinition priceVolumeTrend() {
        return define(PVT).description("Price volume trend").family(VOLUME).models(CUMULATIVE)
                .params().outputs(OutputDef.value("running total"))
                .lookback(s -> 1).warmup(s -> 1)
                .build((c, s) -> {
                    BarSeries series = c.series();
                    int begin = series.getBeginIndex();
                    return single(lambda(c, s, VALUE, (i, self) -> {
                        if (i <= begin) {
                            return 0;
                        }
                        double previousClose = close(series, i - 1);
                        double change = previousClose == 0 ? 0 : (close(series, i) - previousClose) / previousClose;
                        return self.applyAsDouble(i - 1) + change * volume(series, i);
                    }));
                });
    }
}
