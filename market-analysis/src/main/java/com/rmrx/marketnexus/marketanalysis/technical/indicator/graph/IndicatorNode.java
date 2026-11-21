package com.rmrx.marketnexus.marketanalysis.technical.indicator.graph;

import com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.IndicatorDefinition;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.spec.IndicatorSpec;

import java.util.List;

/**
 * One indicator instance of a series' graph.
 *
 * @param spec         canonical spec
 * @param dependencies canonical specs this node reads outputs of (resolved before this node)
 * @param lookback     bars before the current one this node reads ({@link IndicatorDefinition#lookback})
 * @param warmup       bars until usable without a seed, including the longest dependency chain
 */
public record IndicatorNode(IndicatorSpec spec, IndicatorDefinition definition, List<IndicatorSpec> dependencies,
                            int lookback, int warmup) {

    public IndicatorNode {
        dependencies = List.copyOf(dependencies);
    }
}
