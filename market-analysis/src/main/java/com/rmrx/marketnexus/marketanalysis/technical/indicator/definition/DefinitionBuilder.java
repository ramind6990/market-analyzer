package com.rmrx.marketnexus.marketanalysis.technical.indicator.definition;

import com.rmrx.marketnexus.marketanalysis.technical.indicator.schema.IndicatorSpecException;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.schema.ParamDef;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.schema.ParamSchema;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.spec.IndicatorSpec;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.spec.OutputRef;
import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/**
 * Fluent way to describe an indicator type without writing a class:
 * <pre>{@code
 * define("WMA").description("Weighted moving average").family(MOVING_AVERAGE).models(WINDOW)
 *     .params(period(20), source(CLOSE)).outputs(OutputDef.value("average"))
 *     .lookback(s -> s.intParam("period") - 1).warmup(s -> s.intParam("period"))
 *     .build((context, spec) -> Map.of(...));
 * }</pre>
 */
public final class DefinitionBuilder {

    @FunctionalInterface
    public interface Builder {
        Map<String, Indicator<Num>> build(BuildContext context, IndicatorSpec spec);
    }

    private final String type;
    private String description = "";
    private IndicatorFamily family;
    private Set<ComputationModel> models = EnumSet.noneOf(ComputationModel.class);
    private List<ParamDef> params = List.of();
    private List<OutputDef> outputs = List.of();
    private ToIntFunction<IndicatorSpec> lookback;
    private ToIntFunction<IndicatorSpec> warmup;
    private Function<IndicatorSpec, List<OutputRef>> dependencies = spec -> List.of();
    private Predicate<IndicatorSpec> valid = spec -> true;
    private String validMessage = "";

    private DefinitionBuilder(String type) {
        this.type = type;
    }

    public static DefinitionBuilder define(String type) {
        return new DefinitionBuilder(type);
    }

    public DefinitionBuilder description(String description) {
        this.description = description;
        return this;
    }

    public DefinitionBuilder family(IndicatorFamily family) {
        this.family = family;
        return this;
    }

    public DefinitionBuilder models(ComputationModel first, ComputationModel... rest) {
        this.models = EnumSet.of(first, rest);
        return this;
    }

    public DefinitionBuilder params(ParamDef... params) {
        this.params = List.of(params);
        return this;
    }

    public DefinitionBuilder outputs(OutputDef... outputs) {
        this.outputs = List.of(outputs);
        return this;
    }

    public DefinitionBuilder lookback(ToIntFunction<IndicatorSpec> lookback) {
        this.lookback = lookback;
        return this;
    }

    public DefinitionBuilder warmup(ToIntFunction<IndicatorSpec> warmup) {
        this.warmup = warmup;
        return this;
    }

    public DefinitionBuilder dependencies(Function<IndicatorSpec, List<OutputRef>> dependencies) {
        this.dependencies = dependencies;
        return this;
    }

    /** Cross-parameter check run after defaults are filled in. */
    public DefinitionBuilder validate(Predicate<IndicatorSpec> valid, String message) {
        this.valid = valid;
        this.validMessage = message;
        return this;
    }

    public IndicatorDefinition build(Builder builder) {
        Objects.requireNonNull(family, type + ": family");
        Objects.requireNonNull(lookback, type + ": lookback");
        Objects.requireNonNull(warmup, type + ": warmup");
        if (models.isEmpty() || outputs.isEmpty()) {
            throw new IllegalStateException(type + ": models and outputs are required");
        }
        return new LambdaDefinition(this, builder);
    }

    private static final class LambdaDefinition extends AbstractIndicatorDefinition {

        private final ToIntFunction<IndicatorSpec> lookback;
        private final ToIntFunction<IndicatorSpec> warmup;
        private final Function<IndicatorSpec, List<OutputRef>> dependencies;
        private final Predicate<IndicatorSpec> valid;
        private final String validMessage;
        private final Builder builder;

        LambdaDefinition(DefinitionBuilder b, Builder builder) {
            super(b.type, b.description, b.family, b.models, new ParamSchema(b.params), b.outputs);
            this.lookback = b.lookback;
            this.warmup = b.warmup;
            this.dependencies = b.dependencies;
            this.valid = b.valid;
            this.validMessage = b.validMessage;
            this.builder = builder;
        }

        @Override
        public IndicatorSpec canonicalize(IndicatorSpec spec) {
            IndicatorSpec canonical = super.canonicalize(spec);
            if (!valid.test(canonical)) {
                throw new IndicatorSpecException(type() + ": " + validMessage + ": " + canonical);
            }
            return canonical;
        }

        @Override
        public List<OutputRef> dependencies(IndicatorSpec spec) {
            return dependencies.apply(spec);
        }

        @Override
        public int lookback(IndicatorSpec spec) {
            return lookback.applyAsInt(spec);
        }

        @Override
        public int warmup(IndicatorSpec spec) {
            return warmup.applyAsInt(spec);
        }

        @Override
        public Map<String, Indicator<Num>> build(BuildContext context, IndicatorSpec spec) {
            return builder.build(context, spec);
        }
    }
}
