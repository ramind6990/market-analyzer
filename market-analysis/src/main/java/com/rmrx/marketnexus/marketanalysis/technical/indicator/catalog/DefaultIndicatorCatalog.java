package com.rmrx.marketnexus.marketanalysis.technical.indicator.catalog;

import com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.IndicatorDefinition;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.custom.CustomIndicators;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.type.AdxDefinition;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.type.AtrDefinition;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.type.BollingerDefinition;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.type.CandlestickIndicators;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.type.EmaDefinition;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.type.MacdDefinition;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.type.MomentumIndicators;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.type.MovingAverageIndicators;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.type.ObvDefinition;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.type.PriceTransformIndicators;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.type.RsiDefinition;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.type.SmaDefinition;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.type.StatisticalIndicators;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.type.StochasticDefinition;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.type.TrendIndicators;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.type.VolatilityIndicators;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.type.VolumeIndicators;

import java.util.ArrayList;
import java.util.List;

/**
 * Indicator types available: ta4j-based types ({@code definition.type}) plus self-built types without a ta4j
 * counterpart ({@code definition.custom}). RWI and Parabolic SAR are own fast versions (ta4j's classes are
 * pathologically slow). Not supported (see indicator-computation-plan.md section 9): repainting/structure,
 * cross-symbol, cross-timeframe and session-anchored indicators, and outputs that are not one value per bar.
 */
public final class DefaultIndicatorCatalog {

    private DefaultIndicatorCatalog() {
    }

    public static IndicatorCatalog create() {
        List<IndicatorDefinition> definitions = new ArrayList<>(List.of(
                new SmaDefinition(),
                new EmaDefinition(),
                new MacdDefinition(),
                new AdxDefinition(),
                new RsiDefinition(),
                new StochasticDefinition(),
                new AtrDefinition(),
                new BollingerDefinition(),
                new ObvDefinition()));
        definitions.addAll(PriceTransformIndicators.definitions());
        definitions.addAll(MovingAverageIndicators.definitions());
        definitions.addAll(TrendIndicators.definitions());
        definitions.addAll(MomentumIndicators.definitions());
        definitions.addAll(VolatilityIndicators.definitions());
        definitions.addAll(VolumeIndicators.definitions());
        definitions.addAll(StatisticalIndicators.definitions());
        definitions.addAll(CandlestickIndicators.definitions());
        definitions.addAll(CustomIndicators.definitions());
        return new IndicatorCatalog(definitions);
    }
}
