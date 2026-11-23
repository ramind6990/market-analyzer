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
import org.ta4j.core.indicators.volume.MoneyFlowIndexIndicator;
import org.ta4j.core.indicators.volume.VWAPIndicator;
import org.ta4j.core.num.Num;

import java.util.List;
import java.util.Map;

import static com.rmrx.marketanalyzer.marketanalysis.technical.indicator.definition.ComputationModel.COMPOSITE;
import static com.rmrx.marketanalyzer.marketanalysis.technical.indicator.definition.ComputationModel.CUMULATIVE;
import static com.rmrx.marketanalyzer.marketanalysis.technical.indicator.definition.ComputationModel.RECURSIVE;
import static com.rmrx.marketanalyzer.marketanalysis.technical.indicator.definition.ComputationModel.WINDOW;
import static com.rmrx.marketanalyzer.marketanalysis.technical.indicator.definition.DefinitionBuilder.define;
import static com.rmrx.marketanalyzer.marketanalysis.technical.indicator.definition.IndicatorFamily.VOLUME;
import static com.rmrx.marketanalyzer.marketanalysis.technical.indicator.definition.type.DefinitionSupport.*;

/**
 * Volume indicators besides OBV (catalog section 3.6). Formulas follow ta4j 0.18, except that a bar without range
 * has close location value 0 (ta4j: NaN, which makes A/D and the Chaikin oscillator NaN for ever after).
 */
public final class VolumeIndicators {

    public static final String AD = "AD";
    public static final String CMF = "CMF";
    public static final String CHAIKIN_OSC = "CHAIKIN_OSC";
    public static final String MFI = "MFI";
    public static final String VWAP = "VWAP";
    public static final String NVI = "NVI";
    public static final String PVI = "PVI";
    public static final String PVO = "PVO";

    private VolumeIndicators() {
    }

    public static List<IndicatorDefinition> definitions() {
        return List.of(accumulationDistribution(), chaikinMoneyFlow(), chaikinOscillator(), mfi(), vwap(),
                volumeIndex(NVI, false), volumeIndex(PVI, true), pvo());
    }

    /** Accumulation/distribution line: running total of CLV * volume, anchored at the first computed bar. */
    static IndicatorDefinition accumulationDistribution() {
        return define(AD).description("Accumulation/distribution line").family(VOLUME).models(CUMULATIVE)
                .params().outputs(OutputDef.value("running total"))
                .lookback(s -> 0).warmup(s -> 1)
                .build((c, s) -> {
                    BarSeries series = c.series();
                    int begin = series.getBeginIndex();
                    return single(lambda(c, s, VALUE, (i, self) -> i <= begin ? 0
                            : clv(series, i) * volume(series, i) + self.applyAsDouble(i - 1)));
                });
    }

    /** Sum of CLV * volume over n bars / sum of volume. */
    static IndicatorDefinition chaikinMoneyFlow() {
        return define(CMF).description("Chaikin money flow").family(VOLUME).models(WINDOW)
                .params(period(20)).outputs(OutputDef.value("money flow -1..1"))
                .lookback(s -> period(s) - 1).warmup(s -> period(s))
                .build((c, s) -> {
                    BarSeries series = c.series();
                    int begin = series.getBeginIndex();
                    int n = period(s);
                    return single(lambda(c, s, VALUE, (i, self) -> {
                        double flow = 0;
                        double volume = 0;
                        for (int j = Math.max(begin, i - n + 1); j <= i; j++) {
                            flow += clv(series, j) * volume(series, j);
                            volume += volume(series, j);
                        }
                        return volume == 0 ? Double.NaN : flow / volume;
                    }));
                });
    }

    /** EMA(fast) - EMA(slow) of the A/D line; A/D and both EMAs are shared nodes. */
    static IndicatorDefinition chaikinOscillator() {
        return define(CHAIKIN_OSC).description("Chaikin oscillator").family(VOLUME).models(RECURSIVE, COMPOSITE)
                .params(ParamDef.period("fast", 3, "fast EMA of A/D"), ParamDef.period("slow", 10, "slow EMA of A/D"))
                .outputs(OutputDef.value("oscillator"))
                .validate(s -> param(s, "fast") < param(s, "slow"), "fast must be smaller than slow")
                .dependencies(s -> List.of(ref(adEma(param(s, "fast"))), ref(adEma(param(s, "slow")))))
                .lookback(s -> 0).warmup(s -> 0)
                .build((c, s) -> single(formula(c, s, VALUE, x -> x[0] - x[1],
                        c.source(ref(adEma(param(s, "fast")))), c.source(ref(adEma(param(s, "slow")))))));
    }

    private static IndicatorSpec adEma(int period) {
        return ema(period, ref(IndicatorSpec.of(AD)));
    }

    static IndicatorDefinition mfi() {
        return define(MFI).description("Money flow index").family(VOLUME).models(WINDOW)
                .params(period(14)).outputs(OutputDef.value("MFI 0..100"))
                .lookback(s -> period(s)).warmup(s -> period(s) + 1)
                .build((c, s) -> single(wrap(c, s, VALUE, new MoneyFlowIndexIndicator(c.series(), period(s)))));
    }

    /** Rolling VWAP of typical price over n bars (no session reset). */
    static IndicatorDefinition vwap() {
        return define(VWAP).description("Rolling volume-weighted average price").family(VOLUME).models(WINDOW)
                .params(period(20)).outputs(OutputDef.value("VWAP"))
                .lookback(s -> period(s) - 1).warmup(s -> period(s))
                .build((c, s) -> single(wrap(c, s, VALUE, new VWAPIndicator(c.series(), period(s)))));
    }

    /**
     * Negative / positive volume index: starts at 1000, moves with the close only on bars whose volume fell (NVI) or
     * rose (PVI) versus the previous bar. Plus an EMA signal line.
     */
    static IndicatorDefinition volumeIndex(String type, boolean positive) {
        return define(type).description(positive ? "Positive volume index" : "Negative volume index")
                .family(VOLUME).models(CUMULATIVE, RECURSIVE)
                .params(ParamDef.period("signal", 255, "EMA period of the signal line"))
                .outputs(OutputDef.value("index"), OutputDef.publicOutput("signal", "EMA of the index"))
                .lookback(s -> 1).warmup(s -> EMA_FACTOR * param(s, "signal"))
                .build((c, s) -> {
                    BarSeries series = c.series();
                    int begin = series.getBeginIndex();
                    Indicator<Num> index = lambda(c, s, VALUE, (i, self) -> {
                        if (i <= begin) {
                            return 1000;
                        }
                        double previous = self.applyAsDouble(i - 1);
                        double volume = volume(series, i);
                        double previousVolume = volume(series, i - 1);
                        if (positive ? volume > previousVolume : volume < previousVolume) {
                            double previousClose = close(series, i - 1);
                            return previous + (close(series, i) - previousClose) / previousClose * previous;
                        }
                        return previous;
                    });
                    return Map.of(VALUE, index,
                            "signal", ExponentialBlock.ema(index, param(s, "signal"), c.seed(s, "signal")));
                });
    }

    /** Percentage volume oscillator: PPO on volume. */
    static IndicatorDefinition pvo() {
        return TrendIndicators.percentageOscillator(PVO, "Percentage volume oscillator", IndicatorFamily.VOLUME,
                PriceField.VOLUME);
    }
}
