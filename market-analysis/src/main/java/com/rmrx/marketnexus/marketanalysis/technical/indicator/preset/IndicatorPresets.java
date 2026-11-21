package com.rmrx.marketnexus.marketanalysis.technical.indicator.preset;

import com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.type.AdxDefinition;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.type.AtrDefinition;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.type.BollingerDefinition;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.type.CandlestickIndicators;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.type.MomentumIndicators;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.type.MovingAverageIndicators;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.type.PriceTransformIndicators;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.type.StatisticalIndicators;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.type.TrendIndicators;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.type.VolatilityIndicators;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.type.VolumeIndicators;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.type.EmaDefinition;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.type.MacdDefinition;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.type.ObvDefinition;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.type.RsiDefinition;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.type.SmaDefinition;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.type.StochasticDefinition;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.spec.IndicatorSpec;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.spec.PriceField;
import com.rmrx.marketnexus.marketdata.model.Timeframe;

import java.time.Duration;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * v1 indicator set: conventional parameters, computed on every series of a matching timeframe.
 * 200-period averages only from M15 up (on M1/M5 they need too much history for too little meaning).
 */
public final class IndicatorPresets {

    private static final Set<Timeframe> ALL = EnumSet.allOf(Timeframe.class);
    private static final Set<Timeframe> FROM_M15 = Arrays.stream(Timeframe.values())
            .filter(tf -> tf.duration().compareTo(Duration.ofMinutes(15)) >= 0)
            .collect(Collectors.toCollection(() -> EnumSet.noneOf(Timeframe.class)));

    private static final List<IndicatorPreset> V1 = List.of(
            preset("SMA_20", sma(20, PriceField.CLOSE), ALL),
            preset("SMA_50", sma(50, PriceField.CLOSE), ALL),
            preset("SMA_200", sma(200, PriceField.CLOSE), FROM_M15),
            preset("EMA_9", ema(9), ALL),
            preset("EMA_20", ema(20), ALL),
            preset("EMA_50", ema(50), ALL),
            preset("EMA_200", ema(200), FROM_M15),
            preset("VOLUME_SMA_20", sma(20, PriceField.VOLUME), ALL),
            preset("RSI_14", IndicatorSpec.of(RsiDefinition.TYPE).with(RsiDefinition.PERIOD, 14), ALL),
            preset("MACD_12_26_9", IndicatorSpec.of(MacdDefinition.TYPE)
                    .with(MacdDefinition.FAST, 12).with(MacdDefinition.SLOW, 26).with(MacdDefinition.SIGNAL, 9), ALL),
            preset("STOCH_14_3_3", IndicatorSpec.of(StochasticDefinition.TYPE)
                    .with(StochasticDefinition.K_PERIOD, 14).with(StochasticDefinition.K_SMOOTH, 3)
                    .with(StochasticDefinition.D_PERIOD, 3), ALL),
            preset("ATR_14", AtrDefinition.spec(14), ALL),
            preset("BB_20_2", IndicatorSpec.of(BollingerDefinition.TYPE)
                    .with(BollingerDefinition.PERIOD, 20).with(BollingerDefinition.MULTIPLIER, 2), ALL),
            preset("ADX_14", IndicatorSpec.of(AdxDefinition.TYPE)
                    .with(AdxDefinition.DI_LENGTH, 14).with(AdxDefinition.ADX_SMOOTHING, 14), ALL),
            preset("OBV", IndicatorSpec.of(ObvDefinition.TYPE), ALL),
            // the types below use their definition's conventional defaults
            defaults("TR", PriceTransformIndicators.TR),
            defaults("HIGHEST_20", PriceTransformIndicators.HIGHEST),
            defaults("LOWEST_20", PriceTransformIndicators.LOWEST),
            defaults("RMA_14", MovingAverageIndicators.RMA),
            defaults("WMA_20", MovingAverageIndicators.WMA),
            defaults("HMA_9", MovingAverageIndicators.HMA),
            defaults("DEMA_20", MovingAverageIndicators.DEMA),
            defaults("TEMA_20", MovingAverageIndicators.TEMA),
            defaults("ZLEMA_20", MovingAverageIndicators.ZLEMA),
            defaults("KAMA_10_2_30", MovingAverageIndicators.KAMA),
            defaults("VIDYA_9", MovingAverageIndicators.VIDYA),
            defaults("MCGINLEY_14", MovingAverageIndicators.MCGINLEY),
            defaults("VWMA_20", MovingAverageIndicators.VWMA),
            defaults("LSMA_25", MovingAverageIndicators.LSMA),
            defaults("PPO_12_26_9", TrendIndicators.PPO),
            defaults("AROON_25", TrendIndicators.AROON),
            defaults("SUPERTREND_10_3", TrendIndicators.SUPERTREND),
            defaults("ICHIMOKU_9_26_52", TrendIndicators.ICHIMOKU),
            defaults("DPO_20", TrendIndicators.DPO),
            defaults("MASS_INDEX_9_25", TrendIndicators.MASS_INDEX),
            defaults("LINREG_20", TrendIndicators.LINREG),
            defaults("STOCH_RSI_14_14_3_3", MomentumIndicators.STOCH_RSI),
            defaults("WILLIAMS_R_14", MomentumIndicators.WILLIAMS_R),
            defaults("CCI_20", MomentumIndicators.CCI),
            defaults("ROC_10", MomentumIndicators.ROC),
            defaults("CMO_14", MomentumIndicators.CMO),
            defaults("AO_5_34", MomentumIndicators.AO),
            defaults("KST", MomentumIndicators.KST),
            defaults("COPPOCK_14_11_10", MomentumIndicators.COPPOCK),
            defaults("FISHER_10", MomentumIndicators.FISHER),
            defaults("SQUEEZE_20", MomentumIndicators.SQUEEZE),
            defaults("STDDEV_20", VolatilityIndicators.STDDEV),
            defaults("KELTNER_20_10_2", VolatilityIndicators.KELTNER),
            defaults("DONCHIAN_20", VolatilityIndicators.DONCHIAN),
            defaults("ULCER_14", VolatilityIndicators.ULCER),
            defaults("CHANDELIER_22_3", VolatilityIndicators.CHANDELIER),
            defaults("CHOP_14", VolatilityIndicators.CHOP),
            defaults("AD", VolumeIndicators.AD),
            defaults("CMF_20", VolumeIndicators.CMF),
            defaults("CHAIKIN_OSC_3_10", VolumeIndicators.CHAIKIN_OSC),
            defaults("MFI_14", VolumeIndicators.MFI),
            defaults("VWAP_20", VolumeIndicators.VWAP),
            defaults("NVI", VolumeIndicators.NVI),
            defaults("PVI", VolumeIndicators.PVI),
            defaults("PVO_12_26_9", VolumeIndicators.PVO),
            defaults("VARIANCE_20", StatisticalIndicators.VARIANCE),
            defaults("ZSCORE_20", StatisticalIndicators.ZSCORE),
            defaults("KALMAN", StatisticalIndicators.KALMAN),
            defaults("DOJI", CandlestickIndicators.DOJI),
            defaults("BULLISH_ENGULFING", CandlestickIndicators.BULLISH_ENGULFING),
            defaults("BEARISH_ENGULFING", CandlestickIndicators.BEARISH_ENGULFING),
            defaults("BULLISH_HARAMI", CandlestickIndicators.BULLISH_HARAMI),
            defaults("BEARISH_HARAMI", CandlestickIndicators.BEARISH_HARAMI),
            defaults("THREE_WHITE_SOLDIERS", CandlestickIndicators.THREE_WHITE_SOLDIERS),
            defaults("THREE_BLACK_CROWS", CandlestickIndicators.THREE_BLACK_CROWS),
            defaults("HAMMER", CandlestickIndicators.HAMMER),
            defaults("HANGING_MAN", CandlestickIndicators.HANGING_MAN),
            defaults("INVERTED_HAMMER", CandlestickIndicators.INVERTED_HAMMER),
            defaults("SHOOTING_STAR", CandlestickIndicators.SHOOTING_STAR));

    private IndicatorPresets() {
    }

    public static List<IndicatorPreset> all() {
        return V1;
    }

    public static List<IndicatorPreset> forTimeframe(Timeframe timeframe) {
        return V1.stream().filter(p -> p.appliesTo(timeframe)).toList();
    }

    /** Specs to resolve into the graph of a series of this timeframe. */
    public static List<IndicatorSpec> specsFor(Timeframe timeframe) {
        return forTimeframe(timeframe).stream().map(IndicatorPreset::spec).toList();
    }

    private static IndicatorPreset preset(String name, IndicatorSpec spec, Set<Timeframe> timeframes) {
        return new IndicatorPreset(name, spec, timeframes);
    }

    /** Preset on all timeframes with the type's default parameters (filled in by the catalog). */
    private static IndicatorPreset defaults(String name, String type) {
        return preset(name, IndicatorSpec.of(type), ALL);
    }

    private static IndicatorSpec sma(int period, PriceField source) {
        return SmaDefinition.spec(period, source);
    }

    private static IndicatorSpec ema(int period) {
        return EmaDefinition.spec(period, PriceField.CLOSE);
    }
}
