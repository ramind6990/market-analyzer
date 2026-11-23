package com.rmrx.marketanalyzer.marketanalysis.technical.indicator.definition.type;

import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.definition.IndicatorDefinition;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.definition.OutputDef;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.schema.ParamDef;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.spec.PriceField;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.statistics.SigmaIndicator;
import org.ta4j.core.indicators.statistics.VarianceIndicator;
import org.ta4j.core.num.Num;

import java.util.List;
import java.util.Map;

import static com.rmrx.marketanalyzer.marketanalysis.technical.indicator.definition.ComputationModel.RECURSIVE;
import static com.rmrx.marketanalyzer.marketanalysis.technical.indicator.definition.ComputationModel.WINDOW;
import static com.rmrx.marketanalyzer.marketanalysis.technical.indicator.definition.DefinitionBuilder.define;
import static com.rmrx.marketanalyzer.marketanalysis.technical.indicator.definition.IndicatorFamily.STATISTICAL;
import static com.rmrx.marketanalyzer.marketanalysis.technical.indicator.definition.type.DefinitionSupport.*;

/** Statistical indicators (catalog section 3.7) that ta4j 0.18 has. */
public final class StatisticalIndicators {

    public static final String VARIANCE = "VARIANCE";
    public static final String ZSCORE = "ZSCORE";
    public static final String KALMAN = "KALMAN";

    private StatisticalIndicators() {
    }

    public static List<IndicatorDefinition> definitions() {
        return List.of(variance(), zScore(), kalman());
    }

    /** Population variance. */
    static IndicatorDefinition variance() {
        return define(VARIANCE).description("Variance").family(STATISTICAL).models(WINDOW)
                .params(period(20), source(PriceField.CLOSE)).outputs(OutputDef.value("variance"))
                .lookback(s -> period(s) - 1).warmup(s -> period(s))
                .build((c, s) -> single(wrap(c, s, VALUE, new VarianceIndicator(source(c, s), period(s)))));
    }

    /** (x - SMA) / standard deviation. */
    static IndicatorDefinition zScore() {
        return define(ZSCORE).description("Z-score").family(STATISTICAL).models(WINDOW)
                .params(period(20), source(PriceField.CLOSE)).outputs(OutputDef.value("standard deviations from the mean"))
                .lookback(s -> period(s) - 1).warmup(s -> period(s))
                .build((c, s) -> single(wrap(c, s, VALUE, new SigmaIndicator(source(c, s), period(s)))));
    }

    /**
     * One-dimensional Kalman filter as in ta4j (constant-level model, initial state = first input, covariance 1):
     * predict P += Q, gain K = P / (P + R), x += K * (z - x), P *= (1 - K). The covariance is an internal output.
     */
    static IndicatorDefinition kalman() {
        return define(KALMAN).description("Kalman filter (level)").family(STATISTICAL).models(RECURSIVE)
                .params(ParamDef.decimal("processNoise", 1e-4, 1e-12, 1e6, "Q"),
                        ParamDef.decimal("measurementNoise", 1e-3, 1e-12, 1e6, "R"), source(PriceField.CLOSE))
                .outputs(OutputDef.value("filtered level"), OutputDef.internal("covariance", "estimate covariance P"))
                .lookback(s -> 0).warmup(s -> 50)
                .build((c, s) -> {
                    int begin = c.series().getBeginIndex();
                    double q = decimal(s, "processNoise");
                    double r = decimal(s, "measurementNoise");
                    Indicator<Num> z = source(c, s);
                    Indicator<Num> covariance = lambda(c, s, "covariance", (i, self) -> {
                        if (i <= begin) {
                            return 1;
                        }
                        double predicted = self.applyAsDouble(i - 1) + q;
                        double gain = predicted / (predicted + r);
                        return (1 - gain) * predicted;
                    });
                    Indicator<Num> level = lambda(c, s, VALUE, (i, self) -> {
                        if (i <= begin) {
                            return v(z, i);
                        }
                        double predicted = v(covariance, i - 1) + q;
                        double gain = predicted / (predicted + r);
                        double previous = self.applyAsDouble(i - 1);
                        return previous + gain * (v(z, i) - previous);
                    });
                    return Map.of(VALUE, level, "covariance", covariance);
                });
    }
}
