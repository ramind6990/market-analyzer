package com.rmrx.marketnexus.marketanalysis.job.compute;

import com.rmrx.marketnexus.marketanalysis.job.SeriesKey;
import com.rmrx.marketnexus.marketanalysis.job.SeriesTrigger;
import com.rmrx.marketnexus.marketanalysis.job.publish.SeriesIndicatorsPublisher;
import com.rmrx.marketnexus.marketanalysis.job.publish.SeriesIndicatorsUpdated;
import com.rmrx.marketnexus.marketanalysis.store.IndicatorRowWriter;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.compute.ComputationResult;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.compute.IndicatorComputation;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.compute.IndicatorRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

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
                rows.getFirst().openTime(), rows.getLast().openTime(), computation.graph().layout().version(), mode));
        log.info("{} {} {}: {} candle(s) -> {} row(s) up to {}{} in {} ms", series, input.mode(), mode,
                input.candles().size(), rows.size(), rows.getLast().openTime(), more ? " (more)" : "",
                (System.nanoTime() - start) / 1_000_000);
        return Optional.of(result);
    }

    /** LIVE only when the stored newest bar is the newest bar of the series, still fresh after computing. */
    private boolean isLive(SeriesTrigger trigger, JobResult result) {
        return !result.more()
                && result.rows().getLast().openTime().equals(trigger.lastCompleteOpenTime())
                && trigger.isLive(clock.instant());
    }
}
