package com.rmrx.marketanalyzer.marketanalysis.technical.indicator;

import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.catalog.DefaultIndicatorCatalog;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.catalog.IndicatorCatalog;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.compute.ComputationResult;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.compute.IndicatorComputation;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.compute.IndicatorRow;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.graph.IndicatorGraph;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.preset.IndicatorPreset;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.preset.IndicatorPresets;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.series.BarSeriesFactory;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.spec.IndicatorSpec;
import com.rmrx.marketanalyzer.marketdata.model.Candle;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.rmrx.marketanalyzer.marketanalysis.technical.indicator.IndicatorTestData.CANDLES;
import static com.rmrx.marketanalyzer.marketanalysis.technical.indicator.IndicatorTestData.SETS;
import static com.rmrx.marketanalyzer.marketanalysis.technical.indicator.IndicatorTestData.TOLERANCE;
import static com.rmrx.marketanalyzer.marketanalysis.technical.indicator.IndicatorTestData.compareRows;
import static com.rmrx.marketanalyzer.marketanalysis.technical.indicator.IndicatorTestData.relativeDiff;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Indicator engine with all presets (also the 200-period ones normally used only from M15 up), on 5 sets of 1,000
 * synthetic M1 candles:
 * <ol>
 *   <li>full computation (no seed) matches ta4j's own classes, from each node's lookback on (earlier bars are partial
 *       windows); "ta4j NaN where we have a number" is a documented deviation, not a mismatch;</li>
 *   <li>seeded with the first 500 rows of the full computation, bars 500..999 match it, with all candles and with only
 *       the candles a real job loads ({@code historyBars} before the first new bar);</li>
 *   <li>each preset alone, seeded with exactly its own {@code historyBars}, matches its full computation (checks every
 *       declared lookback).</li>
 * </ol>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IndicatorEngineTest {

    private static final int SEEDED_ROWS = 500;

    private IndicatorCatalog catalog;
    private IndicatorGraph graph;
    private IndicatorComputation computation;
    private final List<List<Candle>> sets = new ArrayList<>();
    private final List<List<IndicatorRow>> full = new ArrayList<>();

    @BeforeAll
    void computeAll() {
        catalog = DefaultIndicatorCatalog.create();
        List<IndicatorSpec> specs = IndicatorPresets.all().stream().map(IndicatorPreset::spec).toList();
        graph = catalog.resolve(specs);
        computation = new IndicatorComputation(graph);
        for (int s = 0; s < SETS; s++) {
            List<Candle> candles = IndicatorTestData.candles(s);
            sets.add(candles);
            ComputationResult result = computation.compute(candles, List.of());
            assertEquals(CANDLES, result.rows().size(), "full computation rows");
            full.add(result.rows());
        }
    }

    Stream<Integer> sets() {
        return IntStream.range(0, SETS).boxed();
    }

    @ParameterizedTest(name = "set {0}")
    @MethodSource("sets")
    void fullComputationMatchesTa4j(int set) {
        BarSeries series = BarSeriesFactory.create(sets.get(set));
        List<IndicatorRow> rows = full.get(set);
        Map<String, Double> mismatches = new TreeMap<>();
        int outputs = 0;
        for (IndicatorSpec spec : graph.roots()) {
            int from = Math.max(1, graph.node(spec).lookback());
            for (Map.Entry<String, Indicator<?>> reference : Ta4jReference.build(series, spec).entrySet()) {
                String key = spec + "." + reference.getKey();
                int column = graph.layout().indexOf(catalog.canonicalize(spec.output(reference.getKey())));
                assertTrue(column >= 0, "no column for " + key);
                outputs++;
                double diff = 0;
                for (int i = 0; i < rows.size(); i++) {
                    double expected = Ta4jReference.value(reference.getValue(), i); // every bar in order (Kalman)
                    if (i < from) {
                        continue;
                    }
                    double actual = rows.get(i).values()[column];
                    if (Double.isNaN(expected) && !Double.isNaN(actual)) {
                        continue; // documented deviation: ta4j NaN, we give a number
                    }
                    diff = Math.max(diff, relativeDiff(expected, actual));
                }
                if (diff > TOLERANCE) {
                    mismatches.put(key, diff);
                }
            }
        }
        assertTrue(outputs > 90, "too few outputs compared: " + outputs);
        assertTrue(mismatches.isEmpty(), "outputs differing from ta4j: " + mismatches);
    }

    @ParameterizedTest(name = "set {0}")
    @MethodSource("sets")
    void seededComputationMatchesFull(int set) {
        List<Candle> candles = sets.get(set);
        List<IndicatorRow> stored = full.get(set).subList(0, SEEDED_ROWS);
        List<IndicatorRow> expected = full.get(set).subList(SEEDED_ROWS, CANDLES);

        ComputationResult allCandles = computation.compute(candles, stored);
        assertTrue(allCandles.seeded(), "seeded with all candles");
        assertTrue(compareRows(expected, allCandles.rows()) <= TOLERANCE, "seeded (all candles) differs from full");

        int historyBars = graph.historyBars();
        ComputationResult jobOnly = computation.compute(candles.subList(SEEDED_ROWS - historyBars, CANDLES),
                stored.subList(SEEDED_ROWS - historyBars, SEEDED_ROWS));
        assertTrue(jobOnly.seeded(), "seeded with job candles");
        assertTrue(compareRows(expected, jobOnly.rows()) <= TOLERANCE, "seeded (job candles) differs from full");
    }

    @TestFactory
    Stream<DynamicTest> eachPresetSeededWithItsOwnHistoryBars() {
        List<Candle> candles = sets.getFirst();
        return graph.roots().stream().map(spec -> DynamicTest.dynamicTest(spec.toString(), () -> {
            IndicatorGraph single = catalog.resolve(List.of(spec));
            IndicatorComputation alone = new IndicatorComputation(single);
            List<IndicatorRow> rows = alone.compute(candles, List.of()).rows();
            int h = single.historyBars();
            ComputationResult seeded = alone.compute(candles.subList(SEEDED_ROWS - h, CANDLES),
                    rows.subList(SEEDED_ROWS - h, SEEDED_ROWS));
            assertTrue(seeded.seeded(), "not seeded (history bars " + h + ")");
            double diff = compareRows(rows.subList(SEEDED_ROWS, CANDLES), seeded.rows());
            assertTrue(diff <= TOLERANCE, "seeded differs from full by " + diff + " (history bars " + h + ")");
        }));
    }
}
