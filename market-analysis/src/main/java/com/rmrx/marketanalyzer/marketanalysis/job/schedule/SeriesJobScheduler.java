package com.rmrx.marketanalyzer.marketanalysis.job.schedule;

import com.rmrx.marketanalyzer.marketanalysis.config.AnalysisJobProperties;
import com.rmrx.marketanalyzer.marketanalysis.job.SeriesKey;
import com.rmrx.marketanalyzer.marketanalysis.job.SeriesTrigger;
import com.rmrx.marketanalyzer.marketanalysis.job.compute.JobResult;
import com.rmrx.marketanalyzer.marketanalysis.job.compute.SeriesIndicatorJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Runs series jobs in parallel on a fixed pool fed by a priority queue ({@link JobPriority}).
 * <p>
 * Per series at most one job runs and at most one waits; triggers are merged ({@link SeriesTrigger#merge}).
 * <pre>
 * state     new trigger                       action
 * IDLE      any                               QUEUED, enqueue task
 * QUEUED    any                               merge into queued trigger, no new task; keeps its priority (it already
 *                                             waited), except BACKFILL -> LIVE: task replaced with a LIVE one
 * RUNNING   nothing beyond running trigger    drop
 * RUNNING   newer                             merge into pending (any number of triggers -> one rerun: the rerun
 *                                             computes everything up to the merged trigger)
 * finished  pending present                   QUEUED with pending, enqueue
 * finished  no pending                        IDLE
 * capped    (job hit max-new-bars)            QUEUED again (merged with pending), continues from the stored rows
 * </pre>
 * A failed job counts as finished (logged); the next trigger retries. A task reads its trigger from the slot when it
 * starts, not when it was enqueued.
 */
@Component
public class SeriesJobScheduler implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(SeriesJobScheduler.class);

    private final ConcurrentHashMap<SeriesKey, Slot> slots = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();
    private final ThreadPoolExecutor executor;
    private final SeriesIndicatorJob job;
    private final Clock clock;

    public SeriesJobScheduler(SeriesIndicatorJob job, AnalysisJobProperties properties, Clock clock) {
        this.job = job;
        this.clock = clock;
        AtomicInteger threadNumber = new AtomicInteger();
        this.executor = new ThreadPoolExecutor(properties.threads(), properties.threads(), 0, TimeUnit.MILLISECONDS,
                new PriorityBlockingQueue<>(),
                r -> new Thread(r, "series-job-" + threadNumber.incrementAndGet()));
    }

    public void submit(SeriesTrigger trigger) {
        Task[] enqueue = new Task[1];
        Task[] replaced = new Task[1];
        slots.compute(trigger.series(), (key, slot) -> switch (slot) {
            case null -> {
                enqueue[0] = newTask(trigger);
                yield new Queued(trigger, enqueue[0]);
            }
            case Queued queued -> {
                SeriesTrigger merged = queued.trigger().merge(trigger);
                Task task = queued.task();
                if (!task.priority.live() && merged.isLive(clock.instant())) {
                    replaced[0] = task;
                    task = enqueue[0] = newTask(merged);
                }
                yield new Queued(merged, task);
            }
            case Running running -> {
                if (trigger.coveredBy(running.trigger())) {
                    yield running;
                }
                SeriesTrigger pending = running.pending() == null ? trigger : running.pending().merge(trigger);
                yield new Running(running.trigger(), pending);
            }
        });
        if (replaced[0] != null) {
            executor.remove(replaced[0]);   // already taken by a worker: it sees it is stale and exits
        }
        if (enqueue[0] != null) {
            executor.execute(enqueue[0]);   // execute, not submit: a FutureTask wrapper would lose the priority
        }
    }

    private Task newTask(SeriesTrigger trigger) {
        return new Task(trigger.series(), JobPriority.of(trigger, clock.instant(), sequence.incrementAndGet()));
    }

    private void run(Task task) {
        SeriesTrigger[] trigger = new SeriesTrigger[1];
        slots.computeIfPresent(task.series, (key, slot) -> {
            if (slot instanceof Queued queued && queued.task() == task) {
                trigger[0] = queued.trigger();
                return new Running(queued.trigger(), null);
            }
            return slot;                    // stale task (replaced by a higher-priority one)
        });
        if (trigger[0] == null) {
            return;
        }
        boolean more = false;
        try {
            more = job.run(trigger[0]).map(JobResult::more).orElse(false);
        } catch (RuntimeException e) {
            log.error("Job failed for {} up to {}", task.series, trigger[0].lastCompleteOpenTime(), e);
        } finally {
            finish(task.series, more ? trigger[0] : null);
        }
    }

    /**
     * @param again trigger to run again because the job was capped ({@code max-new-bars}); the rows it stored move
     *              the seed forward, so the rerun continues where it stopped. Usually BACKFILL by then.
     */
    private void finish(SeriesKey series, SeriesTrigger again) {
        Task[] enqueue = new Task[1];
        slots.computeIfPresent(series, (key, slot) -> {
            SeriesTrigger next = slot instanceof Running running ? running.pending() : null;
            if (again != null) {
                next = next == null ? again : next.merge(again);
            }
            if (next != null) {
                enqueue[0] = newTask(next);
                return new Queued(next, enqueue[0]);
            }
            return null;
        });
        if (enqueue[0] != null) {
            executor.execute(enqueue[0]);
        }
    }

    @Override
    public void close() throws InterruptedException {
        executor.shutdownNow();
        executor.awaitTermination(10, TimeUnit.SECONDS);
    }

    private sealed interface Slot permits Queued, Running {
    }

    private record Queued(SeriesTrigger trigger, Task task) implements Slot {
    }

    /** @param pending merged triggers that arrived while running, {@code null} if none */
    private record Running(SeriesTrigger trigger, SeriesTrigger pending) implements Slot {
    }

    private final class Task implements Runnable, Comparable<Task> {

        private final SeriesKey series;
        private final JobPriority priority;

        Task(SeriesKey series, JobPriority priority) {
            this.series = series;
            this.priority = priority;
        }

        @Override
        public void run() {
            SeriesJobScheduler.this.run(this);
        }

        @Override
        public int compareTo(Task other) {
            return priority.compareTo(other.priority);
        }
    }
}
