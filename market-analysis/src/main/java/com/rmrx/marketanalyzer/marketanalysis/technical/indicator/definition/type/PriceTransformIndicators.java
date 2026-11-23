package com.rmrx.marketanalyzer.marketanalysis.technical.indicator.definition.type;

import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.block.TrueRangeBlock;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.block.WindowExtremeBlock;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.definition.IndicatorDefinition;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.definition.OutputDef;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.spec.PriceField;

import java.util.List;

import static com.rmrx.marketanalyzer.marketanalysis.technical.indicator.definition.ComputationModel.STATELESS;
import static com.rmrx.marketanalyzer.marketanalysis.technical.indicator.definition.ComputationModel.WINDOW;
import static com.rmrx.marketanalyzer.marketanalysis.technical.indicator.definition.DefinitionBuilder.define;
import static com.rmrx.marketanalyzer.marketanalysis.technical.indicator.definition.IndicatorFamily.PRICE_TRANSFORM;
import static com.rmrx.marketanalyzer.marketanalysis.technical.indicator.definition.type.DefinitionSupport.*;

/**
 * Price transforms (catalog section 3.1). Typical price, median price and OHLC4 are price sources
 * ({@link PriceField#HLC3}, {@link PriceField#HL2}, {@link PriceField#OHLC4}), usable as input of any indicator.
 */
public final class PriceTransformIndicators {

    public static final String TR = "TR";
    public static final String HIGHEST = "HIGHEST";
    public static final String LOWEST = "LOWEST";

    private PriceTransformIndicators() {
    }

    public static List<IndicatorDefinition> definitions() {
        return List.of(trueRange(), highest(), lowest());
    }

    static IndicatorDefinition trueRange() {
        return define(TR).description("True range").family(PRICE_TRANSFORM).models(STATELESS)
                .params().outputs(OutputDef.value("true range"))
                .lookback(s -> 1).warmup(s -> 2)
                .build((c, s) -> single(new TrueRangeBlock(c.series(), c.seed(s, VALUE))));
    }

    static IndicatorDefinition highest() {
        return define(HIGHEST).description("Highest value over n bars").family(PRICE_TRANSFORM).models(WINDOW)
                .params(period(20), source(PriceField.HIGH)).outputs(OutputDef.value("highest"))
                .lookback(s -> period(s) - 1).warmup(s -> period(s))
                .build((c, s) -> single(WindowExtremeBlock.highest(source(c, s), period(s), c.seed(s, VALUE))));
    }

    static IndicatorDefinition lowest() {
        return define(LOWEST).description("Lowest value over n bars").family(PRICE_TRANSFORM).models(WINDOW)
                .params(period(20), source(PriceField.LOW)).outputs(OutputDef.value("lowest"))
                .lookback(s -> period(s) - 1).warmup(s -> period(s))
                .build((c, s) -> single(WindowExtremeBlock.lowest(source(c, s), period(s), c.seed(s, VALUE))));
    }
}
