package com.rmrx.marketanalyzer.marketanalysis.technical.indicator.catalog;

import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.definition.IndicatorDefinition;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.graph.IndicatorGraph;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.graph.IndicatorNode;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.schema.IndicatorSpecException;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.spec.IndicatorSpec;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.spec.OutputRef;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.spec.ParamValue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** All known indicator types. Canonicalizes specs and resolves them into an {@link IndicatorGraph}. */
public final class IndicatorCatalog {

    private final Map<String, IndicatorDefinition> definitions = new LinkedHashMap<>();

    public IndicatorCatalog(Collection<? extends IndicatorDefinition> definitions) {
        for (IndicatorDefinition definition : definitions) {
            if (this.definitions.put(definition.type(), definition) != null) {
                throw new IllegalArgumentException("duplicate indicator type " + definition.type());
            }
        }
    }

    public Collection<IndicatorDefinition> definitions() {
        return List.copyOf(definitions.values());
    }

    public IndicatorDefinition definition(String type) {
        IndicatorDefinition definition = definitions.get(type);
        if (definition == null) {
            throw new IndicatorSpecException("unknown indicator type " + type);
        }
        return definition;
    }

    /** Validated spec with all defaults filled in, nested indicator sources canonicalized too. */
    public IndicatorSpec canonicalize(IndicatorSpec spec) {
        IndicatorSpec canonical = definition(spec.type()).canonicalize(spec);
        for (Map.Entry<String, ParamValue> param : canonical.params().entrySet()) {
            if (param.getValue() instanceof ParamValue.SourceParam(OutputRef ref)) {
                canonical = canonical.with(param.getKey(), canonicalize(ref));
            }
        }
        return canonical;
    }

    /** Canonical output reference; fails when the type has no such output. */
    public OutputRef canonicalize(OutputRef ref) {
        IndicatorSpec spec = canonicalize(ref.spec());
        boolean known = definition(spec.type()).outputs().stream().anyMatch(o -> o.name().equals(ref.output()));
        if (!known) {
            throw new IndicatorSpecException(spec + " has no output " + ref.output());
        }
        return spec.output(ref.output());
    }

    /**
     * Builds the graph of the requested specs and everything they depend on. The result does not depend on the
     * order of {@code requested}: roots are sorted by canonical form, so the layout version is stable.
     */
    public IndicatorGraph resolve(Collection<IndicatorSpec> requested) {
        List<IndicatorSpec> roots = requested.stream().map(this::canonicalize).distinct().sorted().toList();
        Map<IndicatorSpec, IndicatorNode> resolved = new LinkedHashMap<>();
        Set<IndicatorSpec> visiting = new HashSet<>();
        for (IndicatorSpec root : roots) {
            visit(root, resolved, visiting);
        }
        return new IndicatorGraph(roots, new ArrayList<>(resolved.values()), this::canonicalize);
    }

    private IndicatorNode visit(IndicatorSpec spec, Map<IndicatorSpec, IndicatorNode> resolved,
                                Set<IndicatorSpec> visiting) {
        IndicatorNode existing = resolved.get(spec);
        if (existing != null) {
            return existing;
        }
        if (!visiting.add(spec)) {
            throw new IndicatorSpecException("dependency cycle at " + spec);
        }
        IndicatorDefinition definition = definition(spec.type());
        Set<IndicatorSpec> dependencies = new LinkedHashSet<>();
        for (OutputRef ref : definition.dependencies(spec)) {
            dependencies.add(canonicalize(ref).spec());
        }
        for (OutputRef ref : spec.sourceRefs()) {
            dependencies.add(canonicalize(ref).spec());
        }
        int dependencyWarmup = 0;
        for (IndicatorSpec dependency : dependencies) {
            dependencyWarmup = Math.max(dependencyWarmup, visit(dependency, resolved, visiting).warmup());
        }
        IndicatorNode node = new IndicatorNode(spec, definition, List.copyOf(dependencies),
                definition.lookback(spec), definition.warmup(spec) + dependencyWarmup);
        visiting.remove(spec);
        resolved.put(spec, node);
        return node;
    }
}
