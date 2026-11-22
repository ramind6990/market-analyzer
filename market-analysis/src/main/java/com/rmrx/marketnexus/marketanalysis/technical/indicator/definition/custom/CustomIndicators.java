package com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.custom;

import com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.IndicatorDefinition;

import java.util.ArrayList;
import java.util.List;

/**
 * Indicator types built by us that have no ta4j 0.18 counterpart. ta4j stays the conventions source (EMA started with
 * the first input, same building blocks); the formula each type follows is named in its javadoc. Values are compared
 * against TA-Lib / pandas-ta by the manual reference harness, not at runtime.
 */
public final class CustomIndicators {

    private CustomIndicators() {
    }

    public static List<IndicatorDefinition> definitions() {
        List<IndicatorDefinition> definitions = new ArrayList<>();
        definitions.addAll(CustomPriceTransformIndicators.definitions());
        definitions.addAll(CustomMovingAverageIndicators.definitions());
        definitions.addAll(CustomTrendIndicators.definitions());
        definitions.addAll(CustomMomentumIndicators.definitions());
        definitions.addAll(CustomVolatilityIndicators.definitions());
        definitions.addAll(CustomVolumeIndicators.definitions());
        definitions.addAll(CustomStatisticalIndicators.definitions());
        return definitions;
    }
}
