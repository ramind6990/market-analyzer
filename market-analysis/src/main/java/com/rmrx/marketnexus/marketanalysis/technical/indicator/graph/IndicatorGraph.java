package com.rmrx.marketnexus.marketanalysis.technical.indicator.graph;

import com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.OutputDef;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.spec.IndicatorSpec;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.spec.OutputRef;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * Resolved indicator set of one series: nodes in dependency order (dependencies first), deduplicated by canonical
 * spec, plus the storage layout and the history requirements. Built by {@code IndicatorCatalog.resolve}.
 */
public final class IndicatorGraph {

    private final List<IndicatorSpec> roots;
    private final Map<IndicatorSpec, IndicatorNode> nodes = new LinkedHashMap<>();
    private final IndicatorLayout layout;
    private final int maxLookback;
    private final int maxWarmup;
    private final UnaryOperator<OutputRef> canonicalizer;

    /**
     * @param canonicalizer turns an output reference as written by a definition (possibly without default
     *                      parameters) into its canonical form; supplied by the catalog
     */
    public IndicatorGraph(List<IndicatorSpec> roots, List<IndicatorNode> orderedNodes,
                          UnaryOperator<OutputRef> canonicalizer) {
        this.roots = List.copyOf(roots);
        this.canonicalizer = canonicalizer;
        List<OutputRef> entries = new ArrayList<>();
        List<String> names = new ArrayList<>();
        int lookback = 0;
        int warmup = 0;
        for (IndicatorNode node : orderedNodes) {
            for (IndicatorSpec dependency : node.dependencies()) {
                if (!nodes.containsKey(dependency)) {
                    throw new IllegalArgumentException(node.spec() + " listed before its dependency " + dependency);
                }
            }
            nodes.put(node.spec(), node);
            for (OutputDef output : node.definition().outputs()) {
                OutputRef entry = node.spec().output(output.name());
                entries.add(entry);
                names.add(IndicatorLayout.name(entry, node.definition().version()));
            }
            lookback = Math.max(lookback, node.lookback());
            warmup = Math.max(warmup, node.warmup());
        }
        this.layout = new IndicatorLayout(entries, names);
        this.maxLookback = lookback;
        this.maxWarmup = warmup;
    }

    /** The requested specs (canonical, sorted). */
    public List<IndicatorSpec> roots() {
        return roots;
    }

    /** All nodes, dependencies before dependents. */
    public List<IndicatorNode> nodes() {
        return List.copyOf(nodes.values());
    }

    public IndicatorNode node(IndicatorSpec spec) {
        IndicatorNode node = nodes.get(spec);
        if (node == null) {
            throw new IllegalArgumentException("not part of the graph: " + spec);
        }
        return node;
    }

    public OutputRef canonicalize(OutputRef ref) {
        return canonicalizer.apply(ref);
    }

    public IndicatorLayout layout() {
        return layout;
    }

    /** Largest lookback of any node. */
    public int maxLookback() {
        return maxLookback;
    }

    /**
     * Bars a seeded job needs before its first new bar: candles and stored rows alike. At least one stored row
     * (the last computed bar) is always needed to continue recursive nodes.
     */
    public int historyBars() {
        return Math.max(1, maxLookback);
    }

    /** Bars needed for usable values of every node when computing from candles without a seed. */
    public int maxWarmup() {
        return maxWarmup;
    }

    @Override
    public String toString() {
        return "IndicatorGraph[nodes=" + nodes.size() + ", " + layout + ", maxLookback=" + maxLookback
                + ", maxWarmup=" + maxWarmup + "]";
    }
}
