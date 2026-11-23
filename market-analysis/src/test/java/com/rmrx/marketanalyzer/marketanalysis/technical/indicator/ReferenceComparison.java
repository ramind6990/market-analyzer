package com.rmrx.marketanalyzer.marketanalysis.technical.indicator;

import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.catalog.DefaultIndicatorCatalog;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.catalog.IndicatorCatalog;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.compute.IndicatorComputation;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.compute.IndicatorRow;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.definition.OutputDef;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.graph.IndicatorGraph;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.preset.CustomIndicatorPresets;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.preset.IndicatorPreset;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.spec.IndicatorSpec;
import com.rmrx.marketanalyzer.marketdata.model.Candle;
import com.tictactec.ta.lib.Core;
import com.tictactec.ta.lib.MInteger;
import com.tictactec.ta.lib.RetCode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Manual reference check of the self-built indicator types ({@link CustomIndicatorPresets} only; the ta4j-based types
 * are compared with ta4j by {@link IndicatorEngineTest}). Not a unit test: run {@code main} by hand.
 * <ol>
 *   <li>Computes all custom presets (full computation, no seed) on the demo candle sets.</li>
 *   <li>Writes {@code candles_setN.csv} and {@code ours_setN.csv} to the output directory (default
 *       {@code target/indicator-reference}).</li>
 *   <li>Compares with TA-Lib (in the JVM) for NATR, BOP, T3, TRIX, ULTOSC, PSAR.</li>
 *   <li>Compares with {@code pandas_setN.csv} when present, written by
 *       {@code tools/indicator-reference/pandas_ta_reference.py} from the candle files (run main once, then the
 *       script, then main again).</li>
 * </ol>
 * Only the second half of each set is compared (and not before the node's warm-up), so the different starts of
 * recursive averages (ta4j: first value; TA-Lib/pandas-ta: SMA) have died out. Bars where either side is not finite are
 * skipped and counted (e.g. reference NaN/inf on flat filler candles). Diff = |ours - reference| / largest |reference|
 * of the compared bars.
 */
public final class ReferenceComparison {

    private static final int SETS = 5;
    private static final double TOLERANCE = 1e-6;

    private ReferenceComparison() {
    }

    public static void main(String[] args) throws IOException {
        Path dir = Path.of(args.length > 0 ? args[0] : "target/indicator-reference");
        Files.createDirectories(dir);

        IndicatorCatalog catalog = DefaultIndicatorCatalog.create();
        List<IndicatorPreset> presets = CustomIndicatorPresets.all();
        IndicatorGraph graph = catalog.resolve(presets.stream().map(IndicatorPreset::spec).toList());
        IndicatorComputation computation = new IndicatorComputation(graph);

        // column name "PRESET.output" -> layout index and first comparable bar
        Map<String, Integer> columns = new LinkedHashMap<>();
        Map<String, Integer> warmups = new LinkedHashMap<>();
        for (IndicatorPreset preset : presets) {
            IndicatorSpec spec = catalog.canonicalize(preset.spec());
            var node = graph.node(spec);
            for (OutputDef output : node.definition().outputs()) {
                if (!output.isPublic()) {
                    continue;
                }
                String name = preset.name() + "." + output.name();
                columns.put(name, graph.layout().indexOf(catalog.canonicalize(spec.output(output.name()))));
                warmups.put(name, node.warmup());
            }
        }

        Map<String, Result> results = new TreeMap<>();
        boolean pandasFound = false;
        for (int set = 0; set < SETS; set++) {
            List<Candle> candles = IndicatorTestData.candles(set);
            List<IndicatorRow> rows = computation.compute(candles, List.of()).rows();
            Map<String, double[]> ours = new LinkedHashMap<>();
            columns.forEach((name, column) ->
                    ours.put(name, rows.stream().mapToDouble(r -> r.values()[column]).toArray()));
            writeCandles(dir.resolve("candles_set" + (set + 1) + ".csv"), candles);
            writeColumns(dir.resolve("ours_set" + (set + 1) + ".csv"), ours, candles.size());

            for (Map.Entry<String, double[]> reference : taLib(candles).entrySet()) {
                compare(results, "TA-Lib", reference.getKey(), ours, reference.getValue(), warmups);
            }
            Path pandasFile = dir.resolve("pandas_set" + (set + 1) + ".csv");
            if (Files.exists(pandasFile)) {
                pandasFound = true;
                for (Map.Entry<String, double[]> reference : readColumns(pandasFile).entrySet()) {
                    compare(results, "pandas-ta", reference.getKey(), ours, reference.getValue(), warmups);
                }
            }
        }

        System.out.printf("Custom presets: %d, public outputs: %d, candle sets: %d, files in %s%n%n",
                presets.size(), columns.size(), SETS, dir.toAbsolutePath());
        System.out.printf("%-10s %-38s %8s %8s %12s  %s%n", "reference", "output", "compared", "skipped", "max diff",
                "verdict");
        results.values().stream()
                .sorted((a, b) -> a.reference.equals(b.reference) ? a.output.compareTo(b.output)
                        : a.reference.compareTo(b.reference))
                .forEach(r -> System.out.printf("%-10s %-38s %8d %8d %12.3e  %s%n", r.reference, r.output, r.compared,
                        r.skipped, r.maxDiff, r.compared == 0 ? "NO DATA" : r.maxDiff <= TOLERANCE ? "OK" : "DIFF"));
        List<String> unchecked = columns.keySet().stream()
                .filter(name -> results.values().stream().noneMatch(r -> r.output.equals(name))).toList();
        System.out.println();
        System.out.println("No reference (formula check only): " + unchecked);
        if (!pandasFound) {
            System.out.println("No pandas_setN.csv found: run tools/indicator-reference/pandas_ta_reference.py "
                    + dir.toAbsolutePath() + " and start this class again.");
        }
    }

    // ---------------------------------------------------------------- comparison

    private static final class Result {
        final String reference;
        final String output;
        int compared;
        int skipped;
        double maxDiff;

        Result(String reference, String output) {
            this.reference = reference;
            this.output = output;
        }
    }

    private static void compare(Map<String, Result> results, String reference, String output,
                                Map<String, double[]> ours, double[] expected, Map<String, Integer> warmups) {
        double[] actual = ours.get(output);
        if (actual == null) {
            System.out.println("WARN unknown output in " + reference + " file: " + output);
            return;
        }
        Result result = results.computeIfAbsent(reference + "|" + output, k -> new Result(reference, output));
        int from = Math.max(actual.length / 2, warmups.get(output));
        double scale = 0;
        for (int i = from; i < actual.length; i++) {
            if (Double.isFinite(expected[i])) {
                scale = Math.max(scale, Math.abs(expected[i]));
            }
        }
        scale = scale == 0 ? 1 : scale;
        for (int i = from; i < actual.length; i++) {
            if (!Double.isFinite(expected[i]) || !Double.isFinite(actual[i])) {
                result.skipped++;
                continue;
            }
            result.compared++;
            result.maxDiff = Math.max(result.maxDiff, Math.abs(actual[i] - expected[i]) / scale);
        }
    }

    // ---------------------------------------------------------------- TA-Lib

    private static Map<String, double[]> taLib(List<Candle> candles) {
        int n = candles.size();
        double[] open = new double[n];
        double[] high = new double[n];
        double[] low = new double[n];
        double[] close = new double[n];
        for (int i = 0; i < n; i++) {
            Candle c = candles.get(i);
            open[i] = c.open().doubleValue();
            high[i] = c.high().doubleValue();
            low[i] = c.low().doubleValue();
            close[i] = c.close().doubleValue();
        }
        Core core = new Core();
        Map<String, double[]> out = new LinkedHashMap<>();
        out.put("NATR_14.value", run(n, (b, e, out1) -> core.natr(0, n - 1, high, low, close, 14, b, e, out1)));
        out.put("BOP_14.value", run(n, (b, e, out1) -> core.bop(0, n - 1, open, high, low, close, b, e, out1)));
        out.put("T3_5_0_7.value", run(n, (b, e, out1) -> core.t3(0, n - 1, close, 5, 0.7, b, e, out1)));
        out.put("TRIX_15_9.value", run(n, (b, e, out1) -> core.trix(0, n - 1, close, 15, b, e, out1)));
        out.put("ULTOSC_7_14_28.value",
                run(n, (b, e, out1) -> core.ultOsc(0, n - 1, high, low, close, 7, 14, 28, b, e, out1)));
        out.put("PSAR_0_02_0_2.value",
                run(n, (b, e, out1) -> core.sar(0, n - 1, high, low, 0.02, 0.2, b, e, out1)));
        return out;
    }

    @FunctionalInterface
    private interface TaLibCall {
        RetCode call(MInteger begin, MInteger count, double[] out);
    }

    /** Runs a TA-Lib function and aligns its output to bar indexes (NaN before its first value). */
    private static double[] run(int n, TaLibCall call) {
        MInteger begin = new MInteger();
        MInteger count = new MInteger();
        double[] raw = new double[n];
        RetCode code = call.call(begin, count, raw);
        if (code != RetCode.Success) {
            throw new IllegalStateException("TA-Lib: " + code);
        }
        double[] aligned = new double[n];
        Arrays.fill(aligned, Double.NaN);
        System.arraycopy(raw, 0, aligned, begin.value, count.value);
        return aligned;
    }

    // ---------------------------------------------------------------- CSV

    private static void writeCandles(Path file, List<Candle> candles) throws IOException {
        List<String> lines = new ArrayList<>(candles.size() + 1);
        lines.add("open_time,open,high,low,close,volume");
        for (Candle c : candles) {
            lines.add(c.openTime() + "," + c.open().toPlainString() + "," + c.high().toPlainString() + ","
                    + c.low().toPlainString() + "," + c.close().toPlainString() + "," + c.volume().toPlainString());
        }
        Files.write(file, lines);
    }

    private static void writeColumns(Path file, Map<String, double[]> columns, int rows) throws IOException {
        List<String> lines = new ArrayList<>(rows + 1);
        lines.add(String.join(",", columns.keySet()));
        for (int i = 0; i < rows; i++) {
            StringBuilder line = new StringBuilder();
            for (double[] values : columns.values()) {
                if (!line.isEmpty()) {
                    line.append(',');
                }
                if (Double.isFinite(values[i])) {
                    line.append(String.format(Locale.ROOT, "%.17g", values[i]));
                }
            }
            lines.add(line.toString());
        }
        Files.write(file, lines);
    }

    /** CSV with a header of output names; empty cell, "nan" or "inf" = not finite. */
    private static Map<String, double[]> readColumns(Path file) throws IOException {
        List<String> lines = Files.readAllLines(file);
        String[] names = lines.getFirst().split(",", -1);
        Map<String, double[]> columns = new LinkedHashMap<>();
        for (String name : names) {
            columns.put(name, new double[lines.size() - 1]);
        }
        for (int row = 1; row < lines.size(); row++) {
            String[] cells = lines.get(row).split(",", -1);
            for (int c = 0; c < names.length; c++) {
                columns.get(names[c])[row - 1] = parse(c < cells.length ? cells[c] : "");
            }
        }
        return columns;
    }

    private static double parse(String cell) {
        if (cell.isBlank()) {
            return Double.NaN;
        }
        try {
            return Double.parseDouble(cell);
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }
}
