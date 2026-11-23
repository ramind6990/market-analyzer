package com.rmrx.marketanalyzer.marketanalysis.service;

import com.rmrx.marketanalyzer.marketanalysis.job.SeriesKey;
import com.rmrx.marketanalyzer.marketanalysis.job.compute.SeriesComputations;
import com.rmrx.marketanalyzer.marketanalysis.store.IndicatorLayoutCache;
import com.rmrx.marketanalyzer.marketanalysis.store.IndicatorValueQuery;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.catalog.IndicatorCatalog;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.compute.IndicatorRow;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.definition.IndicatorDefinition;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.definition.OutputDef;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.graph.IndicatorGraph;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.graph.IndicatorLayout;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.spec.IndicatorSpec;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.spec.OutputRef;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Reads stored indicator values. Each row is mapped through its own layout, so history across layout changes works. */
@Service
public class IndicatorService {

    private final IndicatorValueQuery values;
    private final IndicatorLayoutCache layouts;
    private final IndicatorCatalog catalog;
    private final SeriesComputations computations;

    public IndicatorService(IndicatorValueQuery values, IndicatorLayoutCache layouts, IndicatorCatalog catalog,
                            SeriesComputations computations) {
        this.values = values;
        this.layouts = layouts;
        this.catalog = catalog;
        this.computations = computations;
    }

    /**
     * Every indicator of the series' indicator set (the requested ones, not hidden dependencies), newest bars first,
     * at most {@code limit} bars; values keyed by output name. Stored names the current code does not know (an old
     * layout) are skipped; indicators without stored values are left out.
     */
    public List<IndicatorHistory> fetchAllIndicators(SeriesKey series, int limit) {
        IndicatorGraph graph = computations.forTimeframe(series.timeframe()).graph();
        Set<IndicatorSpec> roots = new HashSet<>(graph.roots());
        IndicatorLayout layout = graph.layout();
        Map<String, OutputRef> byName = new HashMap<>();
        for (int i = 0; i < layout.size(); i++) {
            OutputRef entry = layout.entries().get(i);
            if (roots.contains(entry.spec())) {
                byName.put(layout.names().get(i), entry);
            }
        }
        Map<IndicatorSpec, List<IndicatorHistory.Bar>> bars = new LinkedHashMap<>();
        graph.roots().forEach(root -> bars.put(root, new ArrayList<>()));
        for (IndicatorSnapshot snapshot : fetchIndicators(series, null, limit)) {
            Map<IndicatorSpec, Map<String, Double>> perIndicator = new LinkedHashMap<>();
            snapshot.values().forEach((name, value) -> {
                OutputRef ref = byName.get(name);
                if (ref != null) {
                    perIndicator.computeIfAbsent(ref.spec(), s -> new LinkedHashMap<>()).put(ref.output(), value);
                }
            });
            perIndicator.forEach((spec, outputs) -> bars.get(spec).add(new IndicatorHistory.Bar(snapshot.openTime(),
                    outputs)));
        }
        return bars.entrySet().stream()
                .filter(e -> !e.getValue().isEmpty())
                .map(e -> new IndicatorHistory(e.getKey(), e.getValue()))
                .toList();
    }

    /**
     * Newest bars first, at most {@code limit}.
     *
     * @param indicator one indicator (all its public outputs, e.g. MACD line/signal/histogram) or {@code null} for all
     *                  public outputs; bars whose layout does not contain the indicator are skipped
     */
    public List<IndicatorSnapshot> fetchIndicators(SeriesKey series, IndicatorSpec indicator, int limit) {
        List<String> names = indicator == null ? null : publicNames(indicator);
        Set<String> wanted = names == null ? null : new HashSet<>(names);
        return values.newest(series, names, limit).stream()
                .map(row -> snapshot(row, wanted))
                .toList();
    }

    /**
     * One indicator, newest bars first, at most {@code limit}; values keyed by output name ({@code line},
     * {@code signal}, ...). Bars whose layout does not contain the indicator are skipped.
     */
    public IndicatorHistory fetchIndicator(SeriesKey series, IndicatorSpec indicator, int limit) {
        IndicatorSpec spec = catalog.canonicalize(indicator);
        IndicatorDefinition definition = catalog.definition(spec.type());
        Map<String, String> outputByName = new LinkedHashMap<>();
        for (OutputDef output : definition.outputs()) {
            if (output.isPublic()) {
                outputByName.put(IndicatorLayout.name(spec.output(output.name()), definition.version()), output.name());
            }
        }
        List<IndicatorHistory.Bar> bars = fetchIndicators(series, spec, limit).stream()
                .map(snapshot -> {
                    Map<String, Double> byOutput = new LinkedHashMap<>();
                    snapshot.values().forEach((name, value) -> byOutput.put(outputByName.get(name), value));
                    return new IndicatorHistory.Bar(snapshot.openTime(), byOutput);
                })
                .toList();
        return new IndicatorHistory(spec, bars);
    }

    private IndicatorSnapshot snapshot(IndicatorRow row, Set<String> wanted) {
        IndicatorLayoutCache.StoredLayout layout = layouts.get(row.layoutVersion());
        Map<String, Double> result = new LinkedHashMap<>();
        for (int i = 0; i < layout.entries().length; i++) {
            String name = layout.entries()[i];
            boolean include = wanted == null ? !layout.isInternal(i) : wanted.contains(name);
            if (include) {
                double value = row.values()[i];
                result.put(name, Double.isNaN(value) ? null : value);
            }
        }
        return new IndicatorSnapshot(row.openTime(), row.layoutVersion(), result);
    }

    /** Stored names of the public outputs of the indicator (canonical spec, current formula version). */
    private List<String> publicNames(IndicatorSpec indicator) {
        IndicatorSpec spec = catalog.canonicalize(indicator);
        IndicatorDefinition definition = catalog.definition(spec.type());
        return definition.outputs().stream()
                .filter(OutputDef::isPublic)
                .map(output -> IndicatorLayout.name(spec.output(output.name()), definition.version()))
                .toList();
    }
}
