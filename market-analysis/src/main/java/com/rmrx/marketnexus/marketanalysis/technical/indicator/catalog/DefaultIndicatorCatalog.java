package com.rmrx.marketnexus.marketanalysis.technical.indicator.catalog;

import com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.IndicatorDefinition;
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
 * Indicator types available. Not supported (see docs/indicator-catalog.pdf for the full list): ta4j RWI and Parabolic
 * SAR (pathologically slow), indicators ta4j does not have, repainting/structure, cross-symbol, cross-timeframe and
 * session-anchored indicators.
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
        return new IndicatorCatalog(definitions);
    }
}
