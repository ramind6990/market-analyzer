package com.rmrx.marketanalyzer.marketanalysis.job.compute;

import com.rmrx.marketanalyzer.marketanalysis.job.SeriesKey;
import com.rmrx.marketanalyzer.marketanalysis.job.SeriesTrigger;
import com.rmrx.marketanalyzer.marketanalysis.job.publish.SeriesIndicatorsPublisher;
import com.rmrx.marketanalyzer.marketanalysis.job.publish.SeriesIndicatorsUpdated;
import com.rmrx.marketanalyzer.marketanalysis.store.IndicatorRowWriter;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.compute.ComputationResult;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.compute.IndicatorComputation;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.compute.IndicatorRow;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.definition.OutputDef;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.graph.IndicatorGraph;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.graph.IndicatorNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One stateless job: brings the indicators of one series up to {@code trigger.lastCompleteOpenTime()}:
 * load, compute, store, publish {@link SeriesIndicatorsUpdated}.
 */
@Component
public class SeriesIndicatorJob {

    private static final Logger log = LoggerFactory.getLogger(SeriesIndicatorJob.class);

    private final SeriesComputations computations;
    private final JobInputLoader loader;
    private final IndicatorRowWriter writer;
    private final SeriesIndicatorsPublisher publisher;
    private final Clock clock;
    private final Map<Long, int[]> publicIndexes = new ConcurrentHashMap<>();

    public SeriesIndicatorJob(SeriesComputations computations, JobInputLoader loader, IndicatorRowWriter writer,
                              SeriesIndicatorsPublisher publisher, Clock clock) {
        this.computations = computations;
        this.loader = loader;
        this.writer = writer;
        this.publisher = publisher;
        this.clock = clock;
    }

    /** Empty when there was nothing to compute. */
    public Optional<JobResult> run(SeriesTrigger trigger) {
        long start = System.nanoTime();
        SeriesKey series = trigger.series();
        IndicatorComputation computation = computations.forTimeframe(series.timeframe());
        Optional<JobInput> loaded = loader.load(trigger, computation.graph());
        if (loaded.isEmpty()) {
            return Optional.empty();
        }
        JobInput input = loaded.get();
        boolean seeded = input.mode() == JobInput.Mode.SEEDED;
        ComputationResult computed = computation.compute(input.candles(), input.rows());
        if (computed.seeded() != seeded) {
            throw new IllegalStateException(series + ": planned " + input.mode()
                    + " but computation seeded=" + computed.seeded());
        }
        // unseeded: the first maxWarmup rows are not reliable and never stored
        List<IndicatorRow> rows = seeded ? computed.rows()
                : computed.rows().subList(computation.graph().maxWarmup(), computed.rows().size());
        boolean more = input.candles().getLast().openTime().isBefore(trigger.lastCompleteOpenTime());
        JobResult result = new JobResult(input.mode(), seeded ? input.rows().getLast().openTime() : null, rows, more);
        if (rows.isEmpty()) {
            return Optional.of(result);
        }

        writer.upsert(series, rows);
        SeriesIndicatorsUpdated.Mode mode = isLive(trigger, result) ? SeriesIndicatorsUpdated.Mode.LIVE
                : SeriesIndicatorsUpdated.Mode.BACKFILL;
        publisher.publish(new SeriesIndicatorsUpdated(series.exchange(), series.symbol(), series.timeframe(),
                rows.getFirst().openTime(), rows.getLast().openTime(), computation.graph().layout().version(), mode,
                publicValues(computation.graph(), rows.getLast())));
        log.info("{} {} {}: {} candle(s) -> {} row(s) up to {}{} in {} ms", series, input.mode(), mode,
                input.candles().size(), rows.size(), rows.getLast().openTime(), more ? " (more)" : "",
                (System.nanoTime() - start) / 1_000_000);
        return Optional.of(result);
    }

    /** Public outputs of one row, name -> value in layout order; NaN / infinite -> null. */
    private Map<String, Double> publicValues(IndicatorGraph graph, IndicatorRow row) {
        int[] indexes = publicIndexes.computeIfAbsent(graph.layout().version(), v -> publicIndexes(graph));
        List<String> names = graph.layout().names();
        double[] values = row.values();
        Map<String, Double> result = new LinkedHashMap<>(indexes.length * 2);
        for (int i : indexes) {
            double value = values[i];
            result.put(names.get(i), Double.isFinite(value) ? value : null);
        }
        return result;
    }

    /** Layout positions of public outputs (same order as the layout: nodes in graph order, outputs in definition order). */
    private static int[] publicIndexes(IndicatorGraph graph) {
        List<Integer> indexes = new ArrayList<>();
        int position = 0;
        for (IndicatorNode node : graph.nodes()) {
            for (OutputDef output : node.definition().outputs()) {
                if (output.isPublic()) {
                    indexes.add(position);
                }
                position++;
            }
        }
        if (position != graph.layout().size()) {
            throw new IllegalStateException("outputs do not match layout " + graph.layout());
        }
        return indexes.stream().mapToInt(Integer::intValue).toArray();
    }

    /** LIVE only when the stored newest bar is the newest bar of the series, still fresh after computing. */
    private boolean isLive(SeriesTrigger trigger, JobResult result) {
        return !result.more()
                && result.rows().getLast().openTime().equals(trigger.lastCompleteOpenTime())
                && trigger.isLive(clock.instant());
    }
}
