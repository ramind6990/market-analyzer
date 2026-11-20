package com.rmrx.marketnexus.marketdata.service.tracking.book;

import com.rmrx.marketnexus.marketdata.service.tracking.tracker.SeriesKey;
import com.rmrx.marketnexus.marketdata.service.tracking.book.TrackingEvent.Kind;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * {@link TrackingBook} in memory: one sorted map of runs (from → to) per series. Single instance only.
 * <p>
 * Each series is guarded by its own monitor, so concurrent calls never corrupt it; ordering of candles of one series
 * is still the caller's job.
 */
@Component
public class InMemoryTrackingBook implements TrackingBook {

    private final Map<SeriesKey, TreeMap<Instant, Instant>> books = new ConcurrentHashMap<>();

    @Override
    public TrackingEvent apply(SeriesKey series, Instant openTime) {
        TreeMap<Instant, Instant> runs = books.computeIfAbsent(series, key -> new TreeMap<>());
        Duration step = series.timeframe().duration();
        synchronized (runs) {
            if (runs.isEmpty()) {
                runs.put(openTime, openTime);
                return new TrackingEvent(Kind.FIRST, null, newest(runs), true);
            }
            if (!onGrid(runs.firstKey(), openTime, step)) {
                return new TrackingEvent(Kind.MISALIGNED, null, newest(runs), false);
            }

            Map.Entry<Instant, Instant> before = runs.floorEntry(openTime);
            if (before != null && !openTime.isAfter(before.getValue())) {
                return new TrackingEvent(Kind.DUPLICATE, null, newest(runs), false);
            }
            Map.Entry<Instant, Instant> after = runs.higherEntry(openTime);
            ContiguousRun newestBefore = newest(runs);

            boolean joinsBefore = before != null && before.getValue().plus(step).equals(openTime);
            boolean joinsAfter = after != null && after.getKey().minus(step).equals(openTime);

            Kind kind;
            MissingRange newRange = null;
            if (joinsBefore && joinsAfter) {
                runs.remove(after.getKey());
                runs.put(before.getKey(), after.getValue());
                kind = Kind.RANGE_CLOSED;
            } else if (joinsBefore) {
                runs.put(before.getKey(), openTime);
                kind = after == null ? Kind.EXTENDED : Kind.RANGE_SHRUNK;
            } else if (joinsAfter) {
                runs.remove(after.getKey());
                runs.put(openTime, after.getValue());
                kind = before == null ? Kind.BACKFILL : Kind.RANGE_SHRUNK;
            } else {
                runs.put(openTime, openTime);
                if (after == null) {
                    kind = Kind.GAP_OPENED;
                    newRange = new MissingRange(before.getValue().plus(step), openTime.minus(step));
                } else if (before == null) {
                    kind = Kind.BACKFILL_DETACHED;
                    newRange = new MissingRange(openTime.plus(step), after.getKey().minus(step));
                } else {
                    kind = Kind.RANGE_SPLIT;
                }
            }
            ContiguousRun newestAfter = newest(runs);
            return new TrackingEvent(kind, newRange, newestAfter, !newestAfter.equals(newestBefore));
        }
    }

    @Override
    public void load(SeriesKey series, List<ContiguousRun> runs) {
        TreeMap<Instant, Instant> loaded = new TreeMap<>();
        for (ContiguousRun run : runs) {
            loaded.put(run.from(), run.to());
        }
        books.put(series, loaded);
    }

    @Override
    public List<ContiguousRun> runs(SeriesKey series) {
        TreeMap<Instant, Instant> runs = books.get(series);
        if (runs == null) {
            return List.of();
        }
        synchronized (runs) {
            return runs.entrySet().stream()
                    .map(entry -> new ContiguousRun(entry.getKey(), entry.getValue()))
                    .toList();
        }
    }

    @Override
    public List<MissingRange> missingRanges(SeriesKey series) {
        Duration step = series.timeframe().duration();
        List<ContiguousRun> runs = runs(series);
        List<MissingRange> missing = new ArrayList<>(Math.max(0, runs.size() - 1));
        for (int i = 1; i < runs.size(); i++) {
            missing.add(new MissingRange(runs.get(i - 1).to().plus(step), runs.get(i).from().minus(step)));
        }
        return missing;
    }

    @Override
    public Optional<ContiguousRun> newestRun(SeriesKey series) {
        TreeMap<Instant, Instant> runs = books.get(series);
        if (runs == null) {
            return Optional.empty();
        }
        synchronized (runs) {
            return runs.isEmpty() ? Optional.empty() : Optional.of(newest(runs));
        }
    }

    @Override
    public Set<SeriesKey> series() {
        return books.entrySet().stream()
                .filter(entry -> !entry.getValue().isEmpty())
                .map(Map.Entry::getKey)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static ContiguousRun newest(TreeMap<Instant, Instant> runs) {
        Map.Entry<Instant, Instant> last = runs.lastEntry();
        return new ContiguousRun(last.getKey(), last.getValue());
    }

    private static boolean onGrid(Instant anchor, Instant openTime, Duration step) {
        return Duration.between(anchor, openTime).toMillis() % step.toMillis() == 0;
    }
}
