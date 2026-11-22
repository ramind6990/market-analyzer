package com.rmrx.marketnexus.marketanalysis.technical.indicator;

import com.rmrx.marketnexus.marketanalysis.technical.indicator.catalog.DefaultIndicatorCatalog;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.catalog.IndicatorCatalog;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.compute.ComputationResult;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.compute.IndicatorComputation;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.compute.IndicatorRow;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.IndicatorDefinition;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.custom.CustomIndicators;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.OutputDef;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.type.AdxDefinition;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.type.AtrDefinition;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.type.BollingerDefinition;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.type.CandlestickIndicators;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.type.EmaDefinition;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.type.MacdDefinition;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.type.MomentumIndicators;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.type.MovingAverageIndicators;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.type.ObvDefinition;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.type.PriceTransformIndicators;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.type.RsiDefinition;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.type.SmaDefinition;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.type.StatisticalIndicators;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.type.StochasticDefinition;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.type.TrendIndicators;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.type.VolatilityIndicators;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.definition.type.VolumeIndicators;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.graph.IndicatorGraph;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.preset.IndicatorPreset;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.preset.IndicatorPresets;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.series.BarSeriesFactory;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.spec.IndicatorSpec;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.spec.PriceField;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.spec.Source;
import com.rmrx.marketnexus.marketdata.model.Candle;
import com.rmrx.marketnexus.marketdata.model.Timeframe;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.ATRIndicator;
import org.ta4j.core.indicators.RWIHighIndicator;
import org.ta4j.core.indicators.RWILowIndicator;
import org.ta4j.core.indicators.AccelerationDecelerationIndicator;
import org.ta4j.core.indicators.AwesomeOscillatorIndicator;
import org.ta4j.core.indicators.CCIIndicator;
import org.ta4j.core.indicators.CMOIndicator;
import org.ta4j.core.indicators.ChandelierExitLongIndicator;
import org.ta4j.core.indicators.ChandelierExitShortIndicator;
import org.ta4j.core.indicators.ChopIndicator;
import org.ta4j.core.indicators.CoppockCurveIndicator;
import org.ta4j.core.indicators.DPOIndicator;
import org.ta4j.core.indicators.FisherIndicator;
import org.ta4j.core.indicators.KSTIndicator;
import org.ta4j.core.indicators.KalmanFilterIndicator;
import org.ta4j.core.indicators.MACDIndicator;
import org.ta4j.core.indicators.MassIndexIndicator;
import org.ta4j.core.indicators.PPOIndicator;
import org.ta4j.core.indicators.PVOIndicator;
import org.ta4j.core.indicators.ROCIndicator;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.SqueezeProIndicator;
import org.ta4j.core.indicators.StochasticOscillatorKIndicator;
import org.ta4j.core.indicators.StochasticRSIIndicator;
import org.ta4j.core.indicators.UlcerIndexIndicator;
import org.ta4j.core.indicators.WilliamsRIndicator;
import org.ta4j.core.indicators.adx.ADXIndicator;
import org.ta4j.core.indicators.adx.MinusDIIndicator;
import org.ta4j.core.indicators.adx.PlusDIIndicator;
import org.ta4j.core.indicators.aroon.AroonDownIndicator;
import org.ta4j.core.indicators.aroon.AroonOscillatorIndicator;
import org.ta4j.core.indicators.aroon.AroonUpIndicator;
import org.ta4j.core.indicators.averages.DoubleEMAIndicator;
import org.ta4j.core.indicators.averages.EMAIndicator;
import org.ta4j.core.indicators.averages.HMAIndicator;
import org.ta4j.core.indicators.averages.KAMAIndicator;
import org.ta4j.core.indicators.averages.LSMAIndicator;
import org.ta4j.core.indicators.averages.MCGinleyMAIndicator;
import org.ta4j.core.indicators.averages.MMAIndicator;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.averages.TripleEMAIndicator;
import org.ta4j.core.indicators.averages.VIDYAIndicator;
import org.ta4j.core.indicators.averages.VWMAIndicator;
import org.ta4j.core.indicators.averages.WMAIndicator;
import org.ta4j.core.indicators.averages.ZLEMAIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandWidthIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsLowerIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsMiddleIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsUpperIndicator;
import org.ta4j.core.indicators.bollinger.PercentBIndicator;
import org.ta4j.core.indicators.candles.BearishEngulfingIndicator;
import org.ta4j.core.indicators.candles.BearishHaramiIndicator;
import org.ta4j.core.indicators.candles.BullishEngulfingIndicator;
import org.ta4j.core.indicators.candles.BullishHaramiIndicator;
import org.ta4j.core.indicators.candles.DojiIndicator;
import org.ta4j.core.indicators.candles.HammerIndicator;
import org.ta4j.core.indicators.candles.HangingManIndicator;
import org.ta4j.core.indicators.candles.InvertedHammerIndicator;
import org.ta4j.core.indicators.candles.ShootingStarIndicator;
import org.ta4j.core.indicators.candles.ThreeBlackCrowsIndicator;
import org.ta4j.core.indicators.candles.ThreeWhiteSoldiersIndicator;
import org.ta4j.core.indicators.donchian.DonchianChannelLowerIndicator;
import org.ta4j.core.indicators.donchian.DonchianChannelMiddleIndicator;
import org.ta4j.core.indicators.donchian.DonchianChannelUpperIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.indicators.helpers.HighestValueIndicator;
import org.ta4j.core.indicators.helpers.LowPriceIndicator;
import org.ta4j.core.indicators.helpers.LowestValueIndicator;
import org.ta4j.core.indicators.helpers.MedianPriceIndicator;
import org.ta4j.core.indicators.helpers.OpenPriceIndicator;
import org.ta4j.core.indicators.helpers.TRIndicator;
import org.ta4j.core.indicators.helpers.TypicalPriceIndicator;
import org.ta4j.core.indicators.helpers.VolumeIndicator;
import org.ta4j.core.indicators.ichimoku.IchimokuKijunSenIndicator;
import org.ta4j.core.indicators.ichimoku.IchimokuSenkouSpanAIndicator;
import org.ta4j.core.indicators.ichimoku.IchimokuSenkouSpanBIndicator;
import org.ta4j.core.indicators.ichimoku.IchimokuTenkanSenIndicator;
import org.ta4j.core.indicators.keltner.KeltnerChannelLowerIndicator;
import org.ta4j.core.indicators.keltner.KeltnerChannelMiddleIndicator;
import org.ta4j.core.indicators.keltner.KeltnerChannelUpperIndicator;
import org.ta4j.core.indicators.statistics.SigmaIndicator;
import org.ta4j.core.indicators.statistics.SimpleLinearRegressionIndicator;
import org.ta4j.core.indicators.statistics.SimpleLinearRegressionIndicator.SimpleLinearRegressionType;
import org.ta4j.core.indicators.statistics.StandardDeviationIndicator;
import org.ta4j.core.indicators.statistics.VarianceIndicator;
import org.ta4j.core.indicators.supertrend.SuperTrendIndicator;
import org.ta4j.core.indicators.supertrend.SuperTrendLowerBandIndicator;
import org.ta4j.core.indicators.supertrend.SuperTrendUpperBandIndicator;
import org.ta4j.core.indicators.volume.AccumulationDistributionIndicator;
import org.ta4j.core.indicators.volume.ChaikinMoneyFlowIndicator;
import org.ta4j.core.indicators.volume.ChaikinOscillatorIndicator;
import org.ta4j.core.indicators.volume.MoneyFlowIndexIndicator;
import org.ta4j.core.indicators.volume.NVIIndicator;
import org.ta4j.core.indicators.volume.OnBalanceVolumeIndicator;
import org.ta4j.core.indicators.volume.PVIIndicator;
import org.ta4j.core.indicators.volume.VWAPIndicator;
import org.ta4j.core.num.Num;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Manual demo of the indicator engine (run {@link #main}); not part of the application.
 * <ul>
 *   <li>Scenario 1 (worst case, no seed): 5 sets of 1,000 consecutive M1 candles, every preset computed from scratch,
 *       timed, and compared with ta4j's own indicator classes (from each node's lookback on; earlier bars are
 *       partial windows).</li>
 *   <li>Scenario 2 (seeded): the same sets again with the first 500 rows of scenario 1 as stored values; only bars
 *       500..999 are computed. Timed with all 1,000 candles and with only the candles a real job loads;
 *       compared with scenario 1.</li>
 *   <li>Scenario 3 (lookback check): every preset alone in its own graph, seeded with exactly its own
 *       {@code historyBars} candles and rows, compared with its full computation. A too small declared lookback
 *       shows up here.</li>
 * </ul>
 * "ta4j NaN" = ta4j gives NaN where we give a number (documented deviations, e.g. bars without range); not counted as
 * mismatch. The first set runs on a cold JVM, so its timing is pessimistic.
 */
public final class IndicatorDemo {

    private static final int SETS = 5;
    private static final int CANDLES = 1000;
    private static final int SEEDED_ROWS = 500;
    private static final double TOLERANCE = 1e-9;
    private static final Set<String> CUSTOM_TYPES = CustomIndicators.definitions().stream()
            .map(IndicatorDefinition::type).collect(Collectors.toSet());

    private IndicatorDemo() {
    }

    public static void main(String[] args) {
        IndicatorCatalog catalog = DefaultIndicatorCatalog.create();
        // all presets, also the 200-period ones that are normally only used from M15 up
        List<IndicatorSpec> specs = IndicatorPresets.all().stream().map(IndicatorPreset::spec).toList();
        IndicatorGraph graph = catalog.resolve(specs);
        IndicatorComputation computation = new IndicatorComputation(graph);
        int historyBars = graph.historyBars();
        System.out.printf("Catalog: %d types. Graph: %d presets, %d nodes, %d stored values per bar, history bars %d,"
                        + " max warm-up %d%n%n", catalog.definitions().size(), specs.size(), graph.nodes().size(),
                graph.layout().size(), historyBars, graph.maxWarmup());

        List<List<Candle>> sets = new ArrayList<>();
        for (int s = 0; s < SETS; s++) {
            sets.add(candles(s));
        }

        System.out.println("Scenario 1: full computation of 1,000 candles (no seed), compared with ta4j");
        List<List<IndicatorRow>> recorded = new ArrayList<>();
        Map<String, Double> mismatches = new TreeMap<>();
        TreeSet<String> ta4jNaN = new TreeSet<>();
        for (int s = 0; s < SETS; s++) {
            List<Candle> candles = sets.get(s);
            long start = System.nanoTime();
            ComputationResult result = computation.compute(candles, List.of());
            long micros = (System.nanoTime() - start) / 1_000;
            recorded.add(result.rows());

            Comparison ta4j = compareWithTa4j(catalog, graph, candles, result.rows());
            ta4j.mismatches.forEach((k, v) -> mismatches.merge(k, v, Math::max));
            ta4jNaN.addAll(ta4j.ta4jNaN);
            System.out.printf("  set %d: %,8d us, rows %d | vs ta4j: %d outputs, max rel diff %.2e, mismatching %d,"
                            + " ta4j NaN %d -> %s%n",
                    s + 1, micros, result.rows().size(), ta4j.outputs, ta4j.maxDiff, ta4j.mismatches.size(),
                    ta4j.ta4jNaN.size(), ta4j.mismatches.isEmpty() ? "OK" : "MISMATCH");
        }
        mismatches.forEach((k, v) -> System.out.printf("    mismatch: %-60s max rel diff %.3e%n", k, v));
        ta4jNaN.forEach(k -> System.out.println("    ta4j NaN where we have a value: " + k));

        System.out.println();
        System.out.println("Scenario 2: seeded with the first 500 rows of scenario 1, computing bars 500..999");
        for (int s = 0; s < SETS; s++) {
            List<Candle> candles = sets.get(s);
            List<IndicatorRow> stored = recorded.get(s).subList(0, SEEDED_ROWS);
            List<IndicatorRow> expected = recorded.get(s).subList(SEEDED_ROWS, CANDLES);

            long start = System.nanoTime();
            ComputationResult allCandles = computation.compute(candles, stored);
            long allMicros = (System.nanoTime() - start) / 1_000;

            List<Candle> jobCandles = candles.subList(SEEDED_ROWS - historyBars, CANDLES);
            List<IndicatorRow> jobRows = stored.subList(SEEDED_ROWS - historyBars, SEEDED_ROWS);
            start = System.nanoTime();
            ComputationResult jobOnly = computation.compute(jobCandles, jobRows);
            long jobMicros = (System.nanoTime() - start) / 1_000;

            double diffAll = compareRows(expected, allCandles.rows());
            double diffJob = compareRows(expected, jobOnly.rows());
            boolean ok = allCandles.seeded() && jobOnly.seeded() && diffAll <= TOLERANCE && diffJob <= TOLERANCE;
            System.out.printf("  set %d: all 1,000 candles %,7d us (rows %d, diff %.2e) | job candles (%d) %,7d us "
                            + "(rows %d, diff %.2e) -> %s%n",
                    s + 1, allMicros, allCandles.rows().size(), diffAll, jobCandles.size(), jobMicros,
                    jobOnly.rows().size(), diffJob, ok ? "OK" : "MISMATCH");
        }

        System.out.println();
        System.out.println("Scenario 3: each preset alone, seeded with exactly its own history bars (lookback check)");
        int failures = 0;
        for (IndicatorSpec spec : graph.roots()) {
            IndicatorGraph single = catalog.resolve(List.of(spec));
            IndicatorComputation alone = new IndicatorComputation(single);
            List<Candle> candles = sets.getFirst();
            List<IndicatorRow> full = alone.compute(candles, List.of()).rows();
            int h = single.historyBars();
            ComputationResult seeded = alone.compute(candles.subList(SEEDED_ROWS - h, CANDLES),
                    full.subList(SEEDED_ROWS - h, SEEDED_ROWS));
            double diff = compareRows(full.subList(SEEDED_ROWS, CANDLES), seeded.rows());
            if (!seeded.seeded() || diff > TOLERANCE) {
                failures++;
                System.out.printf("  FAIL %s (history bars %d, seeded %b, diff %.3e)%n", spec, h, seeded.seeded(), diff);
            }
        }
        System.out.printf("  %d presets checked, %d failures -> %s%n", graph.roots().size(), failures,
                failures == 0 ? "OK" : "MISMATCH");
    }

    // ---------------------------------------------------------------- ta4j reference

    private record Comparison(int outputs, double maxDiff, Map<String, Double> mismatches, List<String> ta4jNaN) {
    }

    /** Compares every output that has a ta4j counterpart, from the node's lookback on. */
    private static Comparison compareWithTa4j(IndicatorCatalog catalog, IndicatorGraph graph, List<Candle> candles,
                                              List<IndicatorRow> rows) {
        BarSeries series = BarSeriesFactory.create(candles);
        int outputs = 0;
        double maxDiff = 0;
        Map<String, Double> mismatches = new TreeMap<>();
        List<String> nan = new ArrayList<>();
        for (IndicatorSpec spec : graph.roots()) {
            int from = Math.max(1, graph.node(spec).lookback());
            for (Map.Entry<String, Indicator<?>> reference : ta4j(series, spec).entrySet()) {
                String key = spec + "." + reference.getKey();
                int column = graph.layout().indexOf(catalog.canonicalize(spec.output(reference.getKey())));
                if (column < 0) {
                    throw new IllegalStateException("no column for " + key);
                }
                outputs++;
                double outputDiff = 0;
                boolean sawNaN = false;
                for (int i = 0; i < rows.size(); i++) {
                    double expected = value(reference.getValue(), i); // every bar, in order (Kalman is stateful)
                    if (i < from) {
                        continue;
                    }
                    double actual = rows.get(i).values()[column];
                    if (Double.isNaN(expected) && !Double.isNaN(actual)) {
                        sawNaN = true;
                        continue;
                    }
                    outputDiff = Math.max(outputDiff, relativeDiff(expected, actual));
                }
                maxDiff = Math.max(maxDiff, outputDiff);
                if (outputDiff > TOLERANCE) {
                    mismatches.put(key, outputDiff);
                }
                if (sawNaN) {
                    nan.add(key);
                }
            }
        }
        return new Comparison(outputs, maxDiff, mismatches, nan);
    }

    private static double value(Indicator<?> indicator, int index) {
        Object value;
        try {
            value = indicator.getValue(index);
        } catch (RuntimeException e) {
            return Double.NaN; // some ta4j indicators fail on the first bars (negative index)
        }
        if (value instanceof Boolean b) {
            return b ? 1 : 0;
        }
        Num num = (Num) value;
        return num.isNaN() ? Double.NaN : num.doubleValue();
    }

    /** The same indicator built from ta4j's own classes, by output name. Outputs without counterpart are absent. */
    private static Map<String, Indicator<?>> ta4j(BarSeries series, IndicatorSpec spec) {
        Map<String, Indicator<?>> map = new LinkedHashMap<>();
        if (CUSTOM_TYPES.contains(spec.type())) {
            return map; // no ta4j counterpart: compared by the reference harness instead
        }
        String v = OutputDef.VALUE;
        switch (spec.type()) {
            case SmaDefinition.TYPE -> map.put(v, new SMAIndicator(src(series, spec), p(spec, "period")));
            case EmaDefinition.TYPE -> map.put(v, new EMAIndicator(src(series, spec), p(spec, "period")));
            case RsiDefinition.TYPE -> map.put(v, new RSIIndicator(src(series, spec), p(spec, "period")));
            case AtrDefinition.TYPE -> map.put(v, new ATRIndicator(series, p(spec, "period")));
            case MacdDefinition.TYPE -> {
                MACDIndicator macd = new MACDIndicator(src(series, spec), p(spec, "fast"), p(spec, "slow"));
                map.put("line", macd);
                map.put("signal", macd.getSignalLine(p(spec, "signal")));
                map.put("histogram", macd.getHistogram(p(spec, "signal")));
            }
            case BollingerDefinition.TYPE -> {
                Indicator<Num> source = src(series, spec);
                int n = p(spec, "period");
                double kd = d(spec, "multiplier");
                Num k = series.numFactory().numOf(kd);
                BollingerBandsMiddleIndicator middle = new BollingerBandsMiddleIndicator(new SMAIndicator(source, n));
                StandardDeviationIndicator sd = new StandardDeviationIndicator(source, n);
                BollingerBandsUpperIndicator upper = new BollingerBandsUpperIndicator(middle, sd, k);
                BollingerBandsLowerIndicator lower = new BollingerBandsLowerIndicator(middle, sd, k);
                map.put("upper", upper);
                map.put("middle", middle);
                map.put("lower", lower);
                map.put("percentB", new PercentBIndicator(source, n, kd));
                map.put("bandwidth", new BollingerBandWidthIndicator(upper, middle, lower));
            }
            case StochasticDefinition.TYPE -> {
                SMAIndicator k = new SMAIndicator(new StochasticOscillatorKIndicator(series, p(spec, "kPeriod")),
                        p(spec, "kSmooth"));
                map.put("k", k);
                map.put("d", new SMAIndicator(k, p(spec, "dPeriod")));
            }
            case AdxDefinition.TYPE -> {
                int di = p(spec, "diLength");
                map.put("plusDi", new PlusDIIndicator(series, di));
                map.put("minusDi", new MinusDIIndicator(series, di));
                map.put("adx", new ADXIndicator(series, di, p(spec, "adxSmoothing")));
            }
            case ObvDefinition.TYPE -> map.put(v, new OnBalanceVolumeIndicator(series));
            case PriceTransformIndicators.TR -> map.put(v, new TRIndicator(series));
            case PriceTransformIndicators.HIGHEST -> map.put(v, new HighestValueIndicator(src(series, spec), p(spec, "period")));
            case PriceTransformIndicators.LOWEST -> map.put(v, new LowestValueIndicator(src(series, spec), p(spec, "period")));
            case MovingAverageIndicators.RMA -> map.put(v, new MMAIndicator(src(series, spec), p(spec, "period")));
            case MovingAverageIndicators.WMA -> map.put(v, new WMAIndicator(src(series, spec), p(spec, "period")));
            case MovingAverageIndicators.HMA -> map.put(v, new HMAIndicator(src(series, spec), p(spec, "period")));
            case MovingAverageIndicators.DEMA -> map.put(v, new DoubleEMAIndicator(src(series, spec), p(spec, "period")));
            case MovingAverageIndicators.TEMA -> map.put(v, new TripleEMAIndicator(src(series, spec), p(spec, "period")));
            case MovingAverageIndicators.ZLEMA -> map.put(v, new ZLEMAIndicator(src(series, spec), p(spec, "period")));
            case MovingAverageIndicators.KAMA -> map.put(v, new KAMAIndicator(src(series, spec), p(spec, "period"),
                    p(spec, "fast"), p(spec, "slow")));
            case MovingAverageIndicators.VIDYA -> map.put(v, new VIDYAIndicator(src(series, spec), p(spec, "cmoPeriod"),
                    p(spec, "cmoPeriod")));
            case MovingAverageIndicators.MCGINLEY -> map.put(v, new MCGinleyMAIndicator(src(series, spec), p(spec, "period")));
            case MovingAverageIndicators.VWMA -> map.put(v, new VWMAIndicator(src(series, spec), p(spec, "period")));
            case MovingAverageIndicators.LSMA -> map.put(v, new LSMAIndicator(src(series, spec), p(spec, "period")));
            case TrendIndicators.PPO -> map.put("line", new PPOIndicator(src(series, spec), p(spec, "fast"), p(spec, "slow")));
            case TrendIndicators.AROON -> {
                map.put("up", new AroonUpIndicator(series, p(spec, "period")));
                map.put("down", new AroonDownIndicator(series, p(spec, "period")));
                map.put("oscillator", new AroonOscillatorIndicator(series, p(spec, "period")));
            }
            case TrendIndicators.SUPERTREND -> {
                int n = p(spec, "period");
                double m = d(spec, "multiplier");
                map.put(v, new SuperTrendIndicator(series, n, m));
                map.put("upperBand", new SuperTrendUpperBandIndicator(series, new ATRIndicator(series, n), m));
                map.put("lowerBand", new SuperTrendLowerBandIndicator(series, new ATRIndicator(series, n), m));
            }
            case TrendIndicators.ICHIMOKU -> {
                IchimokuTenkanSenIndicator tenkan = new IchimokuTenkanSenIndicator(series, p(spec, "tenkan"));
                IchimokuKijunSenIndicator kijun = new IchimokuKijunSenIndicator(series, p(spec, "kijun"));
                map.put("tenkan", tenkan);
                map.put("kijun", kijun);
                map.put("spanA", new IchimokuSenkouSpanAIndicator(series, tenkan, kijun, p(spec, "displacement")));
                map.put("spanB", new IchimokuSenkouSpanBIndicator(series, p(spec, "senkouB"), p(spec, "displacement")));
            }
            case TrendIndicators.DPO -> map.put(v, new DPOIndicator(src(series, spec), p(spec, "period")));
            case TrendIndicators.MASS_INDEX -> map.put(v, new MassIndexIndicator(series, p(spec, "emaPeriod"),
                    p(spec, "sumPeriod")));
            case TrendIndicators.RWI -> {
                map.put("high", new RWIHighIndicator(series, p(spec, "period")));
                map.put("low", new RWILowIndicator(series, p(spec, "period")));
            }
            case TrendIndicators.LINREG -> {
                map.put(v, new SimpleLinearRegressionIndicator(src(series, spec), p(spec, "period"),
                        SimpleLinearRegressionType.Y));
                map.put("slope", new SimpleLinearRegressionIndicator(src(series, spec), p(spec, "period"),
                        SimpleLinearRegressionType.SLOPE));
            }
            case MomentumIndicators.STOCH_RSI -> map.put(v, new StochasticRSIIndicator(
                    new RSIIndicator(src(series, spec), p(spec, "rsiPeriod")), p(spec, "stochPeriod")));
            case MomentumIndicators.WILLIAMS_R -> map.put(v, new WilliamsRIndicator(series, p(spec, "period")));
            case MomentumIndicators.CCI -> map.put(v, new CCIIndicator(series, p(spec, "period")));
            case MomentumIndicators.ROC -> map.put(v, new ROCIndicator(src(series, spec), p(spec, "period")));
            case MomentumIndicators.CMO -> map.put(v, new CMOIndicator(src(series, spec), p(spec, "period")));
            case MomentumIndicators.AO -> {
                map.put(v, new AwesomeOscillatorIndicator(new MedianPriceIndicator(series), p(spec, "fast"),
                        p(spec, "slow")));
                map.put("accelerator", new AccelerationDecelerationIndicator(series, p(spec, "fast"), p(spec, "slow")));
            }
            case MomentumIndicators.KST -> map.put(v, new KSTIndicator(src(series, spec), p(spec, "sma1"),
                    p(spec, "roc1"), p(spec, "sma2"), p(spec, "roc2"), p(spec, "sma3"), p(spec, "roc3"),
                    p(spec, "sma4"), p(spec, "roc4")));
            case MomentumIndicators.COPPOCK -> map.put(v, new CoppockCurveIndicator(src(series, spec),
                    p(spec, "longRoc"), p(spec, "shortRoc"), p(spec, "wma")));
            case MomentumIndicators.FISHER -> map.put(v, new FisherIndicator(new MedianPriceIndicator(series),
                    p(spec, "period")));
            case MomentumIndicators.SQUEEZE -> map.put(v, new SqueezeProIndicator(series, p(spec, "period"),
                    d(spec, "bbMultiplier"), d(spec, "kcHigh"), d(spec, "kcMid"), d(spec, "kcLow")));
            case VolatilityIndicators.STDDEV -> map.put(v, new StandardDeviationIndicator(src(series, spec),
                    p(spec, "period")));
            case VolatilityIndicators.KELTNER -> {
                KeltnerChannelMiddleIndicator middle = new KeltnerChannelMiddleIndicator(series, p(spec, "emaPeriod"));
                map.put("upper", new KeltnerChannelUpperIndicator(middle, d(spec, "multiplier"), p(spec, "atrPeriod")));
                map.put("middle", middle);
                map.put("lower", new KeltnerChannelLowerIndicator(middle, d(spec, "multiplier"), p(spec, "atrPeriod")));
            }
            case VolatilityIndicators.DONCHIAN -> {
                map.put("upper", new DonchianChannelUpperIndicator(series, p(spec, "period")));
                map.put("middle", new DonchianChannelMiddleIndicator(series, p(spec, "period")));
                map.put("lower", new DonchianChannelLowerIndicator(series, p(spec, "period")));
            }
            case VolatilityIndicators.ULCER -> map.put(v, new UlcerIndexIndicator(src(series, spec), p(spec, "period")));
            case VolatilityIndicators.CHANDELIER -> {
                map.put("long", new ChandelierExitLongIndicator(series, p(spec, "period"), d(spec, "multiplier")));
                map.put("short", new ChandelierExitShortIndicator(series, p(spec, "period"), d(spec, "multiplier")));
            }
            case VolatilityIndicators.CHOP -> map.put(v, new ChopIndicator(series, p(spec, "period"), p(spec, "scale")));
            case VolumeIndicators.AD -> map.put(v, new AccumulationDistributionIndicator(series));
            case VolumeIndicators.CMF -> map.put(v, new ChaikinMoneyFlowIndicator(series, p(spec, "period")));
            case VolumeIndicators.CHAIKIN_OSC -> map.put(v, new ChaikinOscillatorIndicator(series, p(spec, "fast"),
                    p(spec, "slow")));
            case VolumeIndicators.MFI -> map.put(v, new MoneyFlowIndexIndicator(series, p(spec, "period")));
            case VolumeIndicators.VWAP -> map.put(v, new VWAPIndicator(series, p(spec, "period")));
            case VolumeIndicators.NVI -> map.put(v, new NVIIndicator(series));
            case VolumeIndicators.PVI -> map.put(v, new PVIIndicator(series));
            case VolumeIndicators.PVO -> map.put("line", new PVOIndicator(series, p(spec, "fast"), p(spec, "slow")));
            case StatisticalIndicators.VARIANCE -> map.put(v, new VarianceIndicator(src(series, spec), p(spec, "period")));
            case StatisticalIndicators.ZSCORE -> map.put(v, new SigmaIndicator(src(series, spec), p(spec, "period")));
            case StatisticalIndicators.KALMAN -> map.put(v, new KalmanFilterIndicator(src(series, spec),
                    d(spec, "processNoise"), d(spec, "measurementNoise")));
            case CandlestickIndicators.DOJI -> map.put(v, new DojiIndicator(series, p(spec, "period"), d(spec, "bodyFactor")));
            case CandlestickIndicators.BULLISH_ENGULFING -> map.put(v, new BullishEngulfingIndicator(series));
            case CandlestickIndicators.BEARISH_ENGULFING -> map.put(v, new BearishEngulfingIndicator(series));
            case CandlestickIndicators.BULLISH_HARAMI -> map.put(v, new BullishHaramiIndicator(series));
            case CandlestickIndicators.BEARISH_HARAMI -> map.put(v, new BearishHaramiIndicator(series));
            case CandlestickIndicators.THREE_WHITE_SOLDIERS -> map.put(v, new ThreeWhiteSoldiersIndicator(series,
                    p(spec, "period"), series.numFactory().numOf(d(spec, "factor"))));
            case CandlestickIndicators.THREE_BLACK_CROWS -> map.put(v, new ThreeBlackCrowsIndicator(series,
                    p(spec, "period"), d(spec, "factor")));
            case CandlestickIndicators.HAMMER -> map.put(v, new HammerIndicator(series, d(spec, "lowerWickRatio"),
                    d(spec, "upperWickRatio")));
            case CandlestickIndicators.HANGING_MAN -> map.put(v, new HangingManIndicator(series,
                    d(spec, "lowerWickRatio"), d(spec, "upperWickRatio")));
            case CandlestickIndicators.INVERTED_HAMMER -> map.put(v, new InvertedHammerIndicator(series,
                    d(spec, "lowerWickRatio"), d(spec, "upperWickRatio")));
            case CandlestickIndicators.SHOOTING_STAR -> map.put(v, new ShootingStarIndicator(series,
                    d(spec, "lowerWickRatio"), d(spec, "upperWickRatio")));
            default -> throw new IllegalArgumentException("no ta4j reference for " + spec);
        }
        return map;
    }

    private static int p(IndicatorSpec spec, String name) {
        return spec.intParam(name);
    }

    private static double d(IndicatorSpec spec, String name) {
        return spec.decimalParam(name);
    }

    private static Indicator<Num> src(BarSeries series, IndicatorSpec spec) {
        Source source = spec.sourceParam("source");
        if (!(source instanceof PriceField field)) {
            throw new IllegalArgumentException("ta4j reference only for price sources: " + source);
        }
        return switch (field) {
            case OPEN -> new OpenPriceIndicator(series);
            case HIGH -> new HighPriceIndicator(series);
            case LOW -> new LowPriceIndicator(series);
            case CLOSE -> new ClosePriceIndicator(series);
            case VOLUME -> new VolumeIndicator(series);
            case HL2 -> new MedianPriceIndicator(series);
            case HLC3 -> new TypicalPriceIndicator(series);
            case OHLC4 -> throw new IllegalArgumentException("no ta4j OHLC4 indicator");
        };
    }

    // ---------------------------------------------------------------- helpers

    /** Largest relative difference over all values (public and internal outputs); infinite on row mismatch. */
    private static double compareRows(List<IndicatorRow> expected, List<IndicatorRow> actual) {
        if (expected.size() != actual.size()) {
            return Double.POSITIVE_INFINITY;
        }
        double maxDiff = 0;
        for (int i = 0; i < expected.size(); i++) {
            if (!expected.get(i).openTime().equals(actual.get(i).openTime())) {
                return Double.POSITIVE_INFINITY;
            }
            double[] e = expected.get(i).values();
            double[] a = actual.get(i).values();
            for (int c = 0; c < e.length; c++) {
                maxDiff = Math.max(maxDiff, relativeDiff(e[c], a[c]));
            }
        }
        return maxDiff;
    }

    private static double relativeDiff(double expected, double actual) {
        if (Double.isNaN(expected) && Double.isNaN(actual)) {
            return 0;
        }
        if (Double.isNaN(expected) || Double.isNaN(actual)) {
            return Double.POSITIVE_INFINITY;
        }
        return Math.abs(expected - actual) / Math.max(1, Math.abs(expected));
    }

    /** 1,000 consecutive M1 candles, random walk; every 97th candle is a flat filler candle with zero volume. */
    /** Synthetic M1 candles; every 97th bar is a flat zero-volume filler candle. Shared with the reference harness. */
    static List<Candle> candles(int set) {
        Random random = new Random(1000L + set);
        List<Candle> candles = new ArrayList<>(CANDLES);
        double price = 50_000 + 5_000 * set;
        Instant time = Instant.parse("2026-11-01T00:00:00Z").plus(Timeframe.M1.duration().multipliedBy(CANDLES * set));
        for (int i = 0; i < CANDLES; i++) {
            double open = price;
            double close = open;
            double high = open;
            double low = open;
            double volume = 0;
            if (i % 97 != 50) {
                close = open * (1 + random.nextGaussian() * 0.001);
                high = Math.max(open, close) * (1 + random.nextDouble() * 0.0005);
                low = Math.min(open, close) * (1 - random.nextDouble() * 0.0005);
                volume = 0.1 + random.nextDouble() * 5;
            }
            candles.add(new Candle("CRYPTO_DEMO", "BTC/USDT", Timeframe.M1, time,
                    BigDecimal.valueOf(open), BigDecimal.valueOf(high), BigDecimal.valueOf(low),
                    BigDecimal.valueOf(close), BigDecimal.valueOf(volume)));
            price = close;
            time = time.plus(Timeframe.M1.duration());
        }
        return candles;
    }
}
