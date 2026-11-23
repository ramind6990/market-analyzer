package com.rmrx.marketanalyzer.marketanalysis.technical.indicator.definition;

import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.schema.ParamSchema;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.spec.IndicatorSpec;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.spec.OutputRef;
import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * How one indicator type works. One implementation per type; stateless, shared by all series.
 * <p>
 * Seeding contract: every output returned by {@link #build} must be a seeded indicator using
 * {@code context.seed(spec, outputName)}, so its recursion stops at the stored values. Helper parts that are not
 * outputs (e.g. RSI's per-bar gain) are not stored and must only look back within {@link #lookback}.
 */
public interface IndicatorDefinition {

    /** Type name, e.g. {@code RSI}; the first part of the canonical spec. */
    String type();

    String description();

    /**
     * Formula version. Bump it when the computation of an existing type changes: its stored name becomes
     * {@code TYPE@version(...)}, so the layout changes, the series are recomputed into new rows and the values of the
     * old version stay readable under the old layout.
     */
    default int version() {
        return 1;
    }

    IndicatorFamily family();

    Set<ComputationModel> models();

    ParamSchema paramSchema();

    /** All outputs, public and internal, in a fixed order (part of the storage layout). */
    List<OutputDef> outputs();

    /** Validates the spec and fills defaults. Override to add cross-parameter checks (e.g. fast < slow). */
    default IndicatorSpec canonicalize(IndicatorSpec spec) {
        return paramSchema().canonicalize(spec);
    }

    /**
     * Outputs of other indicators this one is built from and that should be shared (e.g. MACD uses EMA(12) and
     * EMA(26)). Indicators given via source parameters are added by the graph automatically.
     */
    default List<OutputRef> dependencies(IndicatorSpec spec) {
        return List.of();
    }

    /**
     * Number of bars before the current one whose input values are read (SMA(n): n - 1, true range: 1, EMA: 0).
     * Applies to candles and to stored values of other nodes alike; own previous value does not count.
     */
    int lookback(IndicatorSpec spec);

    /**
     * Bars this indicator itself needs before its value is usable when computed from candles without a seed,
     * on top of the warm-up of its dependencies.
     */
    int warmup(IndicatorSpec spec);

    /** Builds one seeded ta4j indicator per output name. Dependencies are already built (see {@link BuildContext}). */
    Map<String, Indicator<Num>> build(BuildContext context, IndicatorSpec spec);
}
