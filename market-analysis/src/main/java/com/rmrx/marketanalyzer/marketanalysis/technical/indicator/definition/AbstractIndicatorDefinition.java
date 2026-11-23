package com.rmrx.marketanalyzer.marketanalysis.technical.indicator.definition;

import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.schema.ParamSchema;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.spec.IndicatorSpec;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Holds the static description of a type; subclasses implement lookback, warm-up and build. */
public abstract class AbstractIndicatorDefinition implements IndicatorDefinition {

    /** Bars per period after which an EMA (alpha 2/(n+1)) is considered converged: error ~ e^-8. */
    protected static final int EMA_WARMUP_FACTOR = 4;
    /** Bars per period after which a Wilder average (alpha 1/n) is considered converged: error ~ e^-8. */
    protected static final int WILDER_WARMUP_FACTOR = 8;

    private final String type;
    private final String description;
    private final IndicatorFamily family;
    private final Set<ComputationModel> models;
    private final ParamSchema paramSchema;
    private final List<OutputDef> outputs;

    protected AbstractIndicatorDefinition(String type, String description, IndicatorFamily family,
                                          Set<ComputationModel> models, ParamSchema paramSchema,
                                          List<OutputDef> outputs) {
        this.type = type;
        this.description = description;
        this.family = family;
        this.models = Set.copyOf(EnumSet.copyOf(models));
        this.paramSchema = paramSchema;
        this.outputs = List.copyOf(outputs);
    }

    @Override
    public String type() {
        return type;
    }

    @Override
    public String description() {
        return description;
    }

    @Override
    public IndicatorFamily family() {
        return family;
    }

    @Override
    public Set<ComputationModel> models() {
        return models;
    }

    @Override
    public ParamSchema paramSchema() {
        return paramSchema;
    }

    @Override
    public List<OutputDef> outputs() {
        return outputs;
    }

    /** Spec of another type with the given parameters (canonicalized later by the catalog). */
    protected static IndicatorSpec spec(String type) {
        return IndicatorSpec.of(type);
    }

    protected static int emaWarmup(int period) {
        return EMA_WARMUP_FACTOR * period;
    }

    protected static int wilderWarmup(int period) {
        return WILDER_WARMUP_FACTOR * period;
    }

    @Override
    public String toString() {
        return type;
    }
}
