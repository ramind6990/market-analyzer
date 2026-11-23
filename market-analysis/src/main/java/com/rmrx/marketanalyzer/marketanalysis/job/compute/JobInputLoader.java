package com.rmrx.marketanalyzer.marketanalysis.job.compute;

import com.rmrx.marketanalyzer.marketanalysis.config.AnalysisJobProperties;
import com.rmrx.marketanalyzer.marketanalysis.job.SeriesKey;
import com.rmrx.marketanalyzer.marketanalysis.job.SeriesTrigger;
import com.rmrx.marketanalyzer.marketanalysis.store.CandleReader;
import com.rmrx.marketanalyzer.marketanalysis.store.IndicatorRowReader;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.compute.IndicatorRow;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.graph.IndicatorGraph;
import com.rmrx.marketanalyzer.marketdata.model.Candle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Decides what a job computes and loads it. The trigger only gives the upper bound ({@code lastCompleteOpenTime})
 * and the run length; where to start comes from the stored rows, so lost or duplicate triggers are harmless.
 * A SEEDED job computes exactly the bars after the newest stored row (none of them has a row yet).
 * <ul>
 *   <li>SEEDED: the newest {@code historyBars} stored rows (contiguous) + candles from the oldest of them up to
 *       {@code lastCompleteOpenTime}, at most {@code maxNewBars} new bars. Live: about {@code historyBars + 1}
 *       candles.</li>
 *   <li>FULL (first computation, layout changed, rows purged): the newest
 *       {@code min(completeLength, maxWarmup + initialBars)} candles.</li>
 * </ul>
 * Decision: a series whose complete run is shorter than {@code maxWarmup} is not computed at all (warming up), so
 * every stored value is reliable. Slow timeframes (e.g. D1 with little history) may wait long for their first values.
 */
@Component
public class JobInputLoader {

    private static final Logger log = LoggerFactory.getLogger(JobInputLoader.class);

    private final CandleReader candles;
    private final IndicatorRowReader rows;
    private final AnalysisJobProperties properties;

    public JobInputLoader(CandleReader candles, IndicatorRowReader rows, AnalysisJobProperties properties) {
        this.candles = candles;
        this.rows = rows;
        this.properties = properties;
    }

    /** Empty when there is nothing to compute (up to date, warming up, inconsistent data). */
    public Optional<JobInput> load(SeriesTrigger trigger, IndicatorGraph graph) {
        SeriesKey series = trigger.series();
        Instant to = trigger.lastCompleteOpenTime();
        int historyBars = graph.historyBars();

        List<IndicatorRow> seedRows = rows.last(series, graph.layout().version(), to, historyBars);
        if (!seedRows.isEmpty() && !seedRows.getLast().openTime().isBefore(to)) {
            return Optional.empty();                                    // already computed up to the trigger
        }
        if (seedRows.size() == historyBars && contiguous(seedRows, IndicatorRow::openTime, series)) {
            Instant from = seedRows.getFirst().openTime();
            List<Candle> loaded = candles.range(series, from, to, historyBars + properties.maxNewBars());
            return valid(loaded, from, to, series)
                    ? Optional.of(new JobInput(JobInput.Mode.SEEDED, loaded, seedRows))
                    : Optional.empty();
        }

        int warmup = graph.maxWarmup();
        if (trigger.completeLength() < warmup) {
            log.debug("{} warming up: {} of {} complete candles", series, trigger.completeLength(), warmup);
            return Optional.empty();
        }
        int count = (int) Math.min(trigger.completeLength(), (long) warmup + properties.initialBars());
        List<Candle> loaded = candles.last(series, to, count);
        return loaded.size() >= warmup && valid(loaded, null, to, series)
                ? Optional.of(new JobInput(JobInput.Mode.FULL, loaded, List.of()))
                : Optional.empty();
    }

    /** Gap-free, starts at {@code from} (if given), ends at {@code to} or earlier when capped. */
    private static boolean valid(List<Candle> loaded, Instant from, Instant to, SeriesKey series) {
        boolean ok = !loaded.isEmpty()
                && (from == null || loaded.getFirst().openTime().equals(from))
                && !loaded.getLast().openTime().isAfter(to)
                && contiguous(loaded, Candle::openTime, series);
        if (!ok) {
            log.warn("{}: candles up to {} missing or not contiguous ({} loaded), skipped until next trigger",
                    series, to, loaded.size());
        }
        return ok;
    }

    private static <T> boolean contiguous(List<T> items, Function<T, Instant> openTime, SeriesKey series) {
        Duration step = series.timeframe().duration();
        for (int i = 1; i < items.size(); i++) {
            if (!openTime.apply(items.get(i - 1)).plus(step).equals(openTime.apply(items.get(i)))) {
                return false;
            }
        }
        return true;
    }
}
