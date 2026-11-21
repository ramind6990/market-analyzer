package com.rmrx.marketnexus.marketanalysis.technical.indicator.compute;

import com.rmrx.marketnexus.marketanalysis.technical.indicator.block.PriceIndicator;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.block.Seed;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.BuildContext;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.IndicatorDefinition;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.OutputDef;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.graph.IndicatorGraph;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.graph.IndicatorLayout;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.graph.IndicatorNode;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.series.BarSeriesFactory;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.spec.OutputRef;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.spec.PriceField;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.spec.Source;
import com.rmrx.marketnexus.marketdata.model.Candle;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Computes all nodes of a graph for the bars of one job. Stateless and thread-safe; one instance per graph.
 * <p>
 * Seeded mode: the last {@link IndicatorGraph#historyBars()} bars that have a stored row (same layout version,
 * contiguous, ending at the newest stored bar) seed every node; only the bars after them are computed, so the caller
 * only needs to load {@code historyBars()} candles and rows before the new bars.
 * Otherwise every candle is computed from scratch.
 */
public final class IndicatorComputation {

    private final IndicatorGraph graph;

    public IndicatorComputation(IndicatorGraph graph) {
        this.graph = graph;
    }

    public IndicatorGraph graph() {
        return graph;
    }

    /**
     * @param candles    candles of one series, ascending and gap-free
     * @param storedRows previously stored rows of the series (any order; other layout versions are ignored)
     */
    public ComputationResult compute(List<Candle> candles, Collection<IndicatorRow> storedRows) {
        if (candles.isEmpty()) {
            return new ComputationResult(List.of(), false);
        }
        IndicatorLayout layout = graph.layout();
        BarSeries series = BarSeriesFactory.create(candles);
        Instant[] openTimes = candles.stream().map(Candle::openTime).toArray(Instant[]::new);

        int seedIndex = findSeedIndex(openTimes, storedRows, layout.version());
        Map<OutputRef, Seed> seeds = seedIndex < 0 ? Map.of()
                : seeds(openTimes, storedRows, layout, seedIndex - graph.historyBars() + 1, seedIndex);

        JobContext context = new JobContext(series, seeds);
        for (IndicatorNode node : graph.nodes()) {
            context.build(node);
        }
        List<Indicator<Num>> columns = new ArrayList<>(layout.size());
        for (OutputRef entry : layout.entries()) {
            columns.add(context.output(entry));
        }

        List<IndicatorRow> rows = new ArrayList<>();
        for (int i = seedIndex + 1; i < openTimes.length; i++) {
            double[] values = new double[columns.size()];
            for (int c = 0; c < values.length; c++) {
                values[c] = columns.get(c).getValue(i).doubleValue();
            }
            rows.add(new IndicatorRow(openTimes[i], layout.version(), values));
        }
        return new ComputationResult(rows, seedIndex >= 0);
    }

    /** Index of the newest candle with a stored row, if the {@code historyBars} rows up to it are all present. */
    private int findSeedIndex(Instant[] openTimes, Collection<IndicatorRow> storedRows, long version) {
        Map<Instant, IndicatorRow> byTime = byTime(storedRows, version);
        if (byTime.isEmpty()) {
            return -1;
        }
        int last = -1;
        for (int i = openTimes.length - 1; i >= 0; i--) {
            if (byTime.containsKey(openTimes[i])) {
                last = i;
                break;
            }
        }
        int first = last - graph.historyBars() + 1;
        if (last < 0 || first < 0) {
            return -1;
        }
        for (int i = first; i <= last; i++) {
            if (!byTime.containsKey(openTimes[i])) {
                return -1;
            }
        }
        return last;
    }

    private static Map<OutputRef, Seed> seeds(Instant[] openTimes, Collection<IndicatorRow> storedRows,
                                              IndicatorLayout layout, int first, int last) {
        Map<Instant, IndicatorRow> byTime = byTime(storedRows, layout.version());
        Map<OutputRef, Seed> seeds = new HashMap<>();
        for (int column = 0; column < layout.size(); column++) {
            double[] values = new double[last - first + 1];
            for (int i = first; i <= last; i++) {
                values[i - first] = byTime.get(openTimes[i]).values()[column];
            }
            seeds.put(layout.entries().get(column), Seed.of(first, values));
        }
        return seeds;
    }

    private static Map<Instant, IndicatorRow> byTime(Collection<IndicatorRow> rows, long version) {
        Map<Instant, IndicatorRow> byTime = new HashMap<>();
        for (IndicatorRow row : rows) {
            if (row.layoutVersion() == version) {
                byTime.put(row.openTime(), row);
            }
        }
        return byTime;
    }

    private final class JobContext implements BuildContext {

        private final BarSeries series;
        private final Map<OutputRef, Seed> seeds;
        private final Map<PriceField, Indicator<Num>> prices = new EnumMap<>(PriceField.class);
        private final Map<OutputRef, Indicator<Num>> outputs = new HashMap<>();

        JobContext(BarSeries series, Map<OutputRef, Seed> seeds) {
            this.series = series;
            this.seeds = seeds;
        }

        void build(IndicatorNode node) {
            IndicatorDefinition definition = node.definition();
            Map<String, Indicator<Num>> built = definition.build(this, node.spec());
            for (OutputDef output : definition.outputs()) {
                Indicator<Num> indicator = built.get(output.name());
                if (indicator == null) {
                    throw new IllegalStateException(node.spec() + " did not build output " + output.name());
                }
                outputs.put(node.spec().output(output.name()), indicator);
            }
            if (built.size() != definition.outputs().size()) {
                throw new IllegalStateException(node.spec() + " built undeclared outputs " + built.keySet());
            }
        }

        Indicator<Num> output(OutputRef ref) {
            Indicator<Num> indicator = outputs.get(ref);
            if (indicator == null) {
                indicator = outputs.get(graph.canonicalize(ref));
            }
            if (indicator == null) {
                throw new IllegalStateException("output not built (missing dependency?): " + ref);
            }
            return indicator;
        }

        @Override
        public BarSeries series() {
            return series;
        }

        @Override
        public Indicator<Num> source(Source source) {
            return switch (source) {
                case PriceField field -> prices.computeIfAbsent(field, f -> new PriceIndicator(series, f));
                case OutputRef ref -> output(ref);
            };
        }

        @Override
        public Seed seed(OutputRef output) {
            return seeds.getOrDefault(output, Seed.none());
        }
    }
}
