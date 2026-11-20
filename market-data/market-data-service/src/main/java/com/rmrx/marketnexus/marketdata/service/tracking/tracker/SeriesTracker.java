package com.rmrx.marketnexus.marketdata.service.tracking.tracker;

import com.rmrx.marketnexus.marketdata.service.tracking.book.ContiguousRun;
import com.rmrx.marketnexus.marketdata.service.tracking.book.MissingRange;
import com.rmrx.marketnexus.marketdata.service.tracking.book.TrackingBook;
import com.rmrx.marketnexus.marketdata.service.tracking.gap.CandleGap;
import com.rmrx.marketnexus.marketdata.service.tracking.gap.CandleGapRepository;
import com.rmrx.marketnexus.marketdata.service.tracking.book.TrackingEvent;
import com.rmrx.marketnexus.marketdata.service.tracking.book.TrackingEvent.Kind;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Mediator between the places that store closed candles (live M1 ingestion, derived candles, reconciliation) and
 * the consumers of series state ({@link TrackingListener}s, e.g. the Kafka publisher for analysis).
 * <p>
 * Callers hand over closed candles <b>after</b> they are committed. Per series, one call at a time: the book is
 * updated, gaps are written to / settled in {@code candle_gap}, and listeners are told when the newest run changed.
 * Failures are logged and swallowed, so tracking never breaks storing or publishing candles; a restart rebuilds the
 * book from the database.
 */
@Service
public class SeriesTracker {

    private static final Logger log = LoggerFactory.getLogger(SeriesTracker.class);

    private final TrackingBook book;
    private final CandleGapRepository gapRepository;
    private final List<TrackingListener> listeners;
    private final Clock clock;

    private final Map<SeriesKey, ReentrantLock> locks = new ConcurrentHashMap<>();
    /** Unfilled gaps per series, loaded from the database on first use. Only touched under the series lock. */
    private final Map<SeriesKey, List<CandleGap>> openGaps = new ConcurrentHashMap<>();

    @Autowired
    public SeriesTracker(TrackingBook book, CandleGapRepository gapRepository, List<TrackingListener> listeners) {
        this(book, gapRepository, listeners, Clock.systemUTC());
    }

    SeriesTracker(TrackingBook book, CandleGapRepository gapRepository, List<TrackingListener> listeners, Clock clock) {
        this.book = book;
        this.gapRepository = gapRepository;
        this.listeners = List.copyOf(listeners);
        this.clock = clock;
    }

    /** One committed closed candle. */
    public void onClosedCandle(SeriesKey series, Instant openTime) {
        onClosedCandles(series, List.of(openTime));
    }

    /** Several committed closed candles of one series (e.g. a reconciliation chunk); applied in ascending order. */
    public void onClosedCandles(SeriesKey series, List<Instant> openTimes) {
        if (openTimes.isEmpty()) {
            return;
        }
        List<Instant> sorted = openTimes.stream().sorted().toList();
        ReentrantLock lock = locks.computeIfAbsent(series, key -> new ReentrantLock());
        lock.lock();
        try {
            track(series, sorted);
        } catch (RuntimeException e) {
            log.error("Tracking failed for {} ({} candles from {})", series, sorted.size(), sorted.getFirst(), e);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Replaces what is known about a series (startup rebuild): its runs and its unfilled gaps. Gaps the runs already
     * cover are marked filled right away. Listeners are not told.
     */
    public void load(SeriesKey series, List<ContiguousRun> runs, List<CandleGap> unfilledGaps) {
        ReentrantLock lock = locks.computeIfAbsent(series, key -> new ReentrantLock());
        lock.lock();
        try {
            book.load(series, runs);
            openGaps.put(series, new ArrayList<>(unfilledGaps));
            settleGaps(series, clock.instant());
        } finally {
            lock.unlock();
        }
    }

    private void track(SeriesKey series, List<Instant> openTimes) {
        Instant now = clock.instant();
        boolean newestRunChanged = false;
        boolean historyChanged = false;
        for (Instant openTime : openTimes) {
            TrackingEvent event = book.apply(series, openTime);
            newestRunChanged |= event.newestRunChanged();
            switch (event.kind()) {
                case GAP_OPENED -> {
                    log.info("Gap in {}: {} (next candle {})", series, event.newRange(), openTime);
                    gapRepository.record(series, event.newRange().from(), event.newRange().to(), now)
                            .ifPresent(gap -> remember(series, gap));
                }
                case BACKFILL_DETACHED -> log.debug("Backfill in {} before known history: {} still missing",
                        series, event.newRange());
                case MISALIGNED -> log.warn("Candle {} of {} is off the timeframe grid, ignored", openTime, series);
                case DUPLICATE, FIRST -> {
                }
                default -> historyChanged = true;
            }
        }
        if (historyChanged) {
            settleGaps(series, now);
        }
        if (newestRunChanged) {
            book.newestRun(series).ifPresent(run -> notifyListeners(series, run));
        }
    }

    /** Marks unfilled gaps of a series filled once the book has every candle of them. */
    private void settleGaps(SeriesKey series, Instant now) {
        List<CandleGap> gaps = openGaps(series);
        if (gaps.isEmpty()) {
            return;
        }
        List<ContiguousRun> runs = book.runs(series);
        if (runs.isEmpty()) {
            return;
        }
        Instant oldest = runs.getFirst().from();
        Instant newest = runs.getLast().to();
        List<MissingRange> missing = book.missingRanges(series);
        gaps.removeIf(gap -> {
            boolean inside = !gap.from().isBefore(oldest) && !gap.to().isAfter(newest);
            boolean complete = inside && missing.stream().noneMatch(range -> range.overlaps(gap.from(), gap.to()));
            if (complete) {
                gapRepository.markFilled(gap.id(), now);
                log.info("Gap in {} filled: [{} .. {}]", series, gap.from(), gap.to());
            }
            return complete;
        });
    }

    private void remember(SeriesKey series, CandleGap gap) {
        List<CandleGap> gaps = openGaps(series);
        gaps.removeIf(known -> known.id() == gap.id());
        gaps.add(gap);
    }

    private List<CandleGap> openGaps(SeriesKey series) {
        return openGaps.computeIfAbsent(series, key -> new ArrayList<>(gapRepository.findUnfilled(key)));
    }

    private void notifyListeners(SeriesKey series, ContiguousRun newest) {
        long length = newest.length(series.timeframe());
        for (TrackingListener listener : listeners) {
            try {
                listener.onNewestRunChanged(series, length, newest.to());
            } catch (RuntimeException e) {
                log.error("Tracking listener {} failed for {}", listener.getClass().getSimpleName(), series, e);
            }
        }
    }

    /** Runs of a series, oldest first, as currently known. */
    public List<ContiguousRun> runs(SeriesKey series) {
        return book.runs(series);
    }

    /** Newest run of a series, as currently known. */
    public Optional<ContiguousRun> newestRun(SeriesKey series) {
        return book.newestRun(series);
    }
}
