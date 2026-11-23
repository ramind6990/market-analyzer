package com.rmrx.marketanalyzer.marketanalysis.technical.indicator.preset;

import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.definition.custom.CustomMomentumIndicators;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.definition.custom.CustomMovingAverageIndicators;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.definition.custom.CustomPriceTransformIndicators;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.definition.custom.CustomStatisticalIndicators;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.definition.custom.CustomTrendIndicators;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.definition.custom.CustomVolatilityIndicators;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.definition.custom.CustomVolumeIndicators;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.spec.IndicatorSpec;
import com.rmrx.marketanalyzer.marketdata.model.Timeframe;

import java.util.EnumSet;
import java.util.List;

/**
 * Presets of the self-built types ({@code definition.custom}), each with its definition's conventional defaults on
 * all timeframes. Part of {@link IndicatorPresets#all()}; listed separately so the reference harness can compare
 * exactly these against TA-Lib / pandas-ta.
 */
public final class CustomIndicatorPresets {

    private static final List<IndicatorPreset> ALL = List.of(
            defaults("HEIKIN_ASHI", CustomPriceTransformIndicators.HEIKIN_ASHI),
            defaults("ELDER_RAY_13", CustomTrendIndicators.ELDER_RAY),
            defaults("BOP_14", CustomMomentumIndicators.BOP),
            defaults("NATR_14", CustomVolatilityIndicators.NATR),
            defaults("MA_ENVELOPE_20_2_5", CustomVolatilityIndicators.MA_ENVELOPE),
            defaults("RVOL_20", CustomVolumeIndicators.RVOL),
            defaults("FORCE_INDEX_13", CustomVolumeIndicators.FORCE_INDEX),
            defaults("PVT", CustomVolumeIndicators.PVT),
            defaults("LOG_RETURN_1", CustomStatisticalIndicators.LOG_RETURN),
            defaults("T3_5_0_7", CustomMovingAverageIndicators.T3),
            defaults("TRIX_15_9", CustomTrendIndicators.TRIX),
            defaults("STC_23_50_10", CustomTrendIndicators.STC),
            defaults("TSI_25_13_13", CustomMomentumIndicators.TSI),
            defaults("CONNORS_RSI_3_2_100", CustomMomentumIndicators.CONNORS_RSI),
            defaults("CHAIKIN_VOL_10_10", CustomVolatilityIndicators.CHAIKIN_VOL),
            defaults("KLINGER_34_55_13", CustomVolumeIndicators.KLINGER),
            defaults("ALMA_9_0_85_6", CustomMovingAverageIndicators.ALMA),
            defaults("FRAMA_16", CustomMovingAverageIndicators.FRAMA),
            defaults("ALLIGATOR_13_8_5", CustomMovingAverageIndicators.ALLIGATOR),
            defaults("VORTEX_14", CustomTrendIndicators.VORTEX),
            defaults("ULTOSC_7_14_28", CustomMomentumIndicators.ULTOSC),
            defaults("RVGI_10", CustomMomentumIndicators.RVGI),
            defaults("SQUEEZE_MOMENTUM_20", CustomMomentumIndicators.SQUEEZE_MOMENTUM),
            defaults("EOM_14", CustomVolumeIndicators.EOM),
            defaults("HV_20", CustomVolatilityIndicators.HV),
            defaults("PERCENT_RANK_100", CustomStatisticalIndicators.PERCENT_RANK),
            defaults("MEDIAN_20", CustomStatisticalIndicators.QUANTILE),
            defaults("R_SQUARED_20", CustomStatisticalIndicators.R_SQUARED),
            defaults("AUTOCORR_20_1", CustomStatisticalIndicators.AUTOCORR),
            defaults("HURST_100", CustomStatisticalIndicators.HURST),
            defaults("ENTROPY_10", CustomStatisticalIndicators.ENTROPY),
            defaults("FRACTAL_DIM_30", CustomStatisticalIndicators.FRACTAL_DIM),
            defaults("PSAR_0_02_0_2", CustomTrendIndicators.PSAR));

    private CustomIndicatorPresets() {
    }

    public static List<IndicatorPreset> all() {
        return ALL;
    }

    private static IndicatorPreset defaults(String name, String type) {
        return new IndicatorPreset(name, IndicatorSpec.of(type), EnumSet.allOf(Timeframe.class));
    }
}
