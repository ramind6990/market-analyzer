package com.rmrx.marketnexus.marketdata.service.reconciliation.job.execution;

import com.rmrx.marketnexus.marketdata.service.reconciliation.job.ReconciliationJob;
import com.rmrx.marketnexus.marketdata.service.reconciliation.job.ReconciliationJobRepository;

import com.rmrx.marketnexus.marketdata.service.config.ReconciliationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Executes reconciliation jobs continuously with a dispatcher and a pool of executor threads.
 * <ul>
 *   <li>Dispatcher (1 thread): when the in-memory queue holds no more than {@code threads} jobs, claims the next
 *       {@code claim-batch-size} due jobs in one statement ({@link ReconciliationJobRepository#claimBatch}, priority
 *       order, marked RUNNING) and queues them; when nothing is due it sleeps {@code idle-poll-interval}. Small batches,
 *       refilled when low, keep newly registered high-priority jobs from waiting behind a long queue.</li>
 *   <li>Executors ({@code threads}): take a job, wait for the SHARED rate limiter, run ONE chunk
 *       ({@link ReconciliationJobExecutor}). The shared limiter keeps the total at {@code requests-per-minute}, however
 *       many threads there are; extra threads only hide latency.</li>
 * </ul>
 * A job is never executed twice at once: claimed rows are RUNNING, so later claims can't return them. On stop, jobs still
 * queued are released back to PENDING; on start, jobs left RUNNING by a crash are reset to PENDING.
 * Not started automatically: the application starts it after the startup registration run.
 */
@Component
public class ReconciliationWorker implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationWorker.class);

    private final ReconciliationJobRepository jobRepository;
    private final ReconciliationJobExecutor executor;
    private final ReconciliationRateLimiter rateLimiter;
    private final ReconciliationProperties.Worker settings;
    private final LinkedBlockingQueue<ReconciliationJob> queue = new LinkedBlockingQueue<>();
    private final List<Thread> threads = new ArrayList<>();
    private volatile boolean running;

    public ReconciliationWorker(ReconciliationJobRepository jobRepository, ReconciliationJobExecutor executor,
                                ReconciliationRateLimiter rateLimiter, ReconciliationProperties properties) {
        this.jobRepository = jobRepository;
        this.executor = executor;
        this.rateLimiter = rateLimiter;
        this.settings = properties.worker();
    }

    @Override
    public synchronized void start() {
        if (running) {
            return;
        }
        int reset = jobRepository.resetRunning();
        if (reset > 0) {
            log.warn("Reset {} job(s) left RUNNING (previous shutdown/crash) to PENDING", reset);
        }
        running = true;
        threads.add(newThread(this::dispatchLoop, "reconciliation-dispatcher"));
        for (int i = 1; i <= settings.threads(); i++) {
            threads.add(newThread(this::executeLoop, "reconciliation-worker-" + i));
        }
        threads.forEach(Thread::start);
        log.info("Reconciliation worker started: {} thread(s), claim batch {}, {} request(s)/min", settings.threads(),
                settings.claimBatchSize(), settings.requestsPerMinute());
    }

    @Override
    public synchronized void stop() {
        if (!running) {
            return;
        }
        running = false;
        threads.forEach(Thread::interrupt);
        for (Thread thread : threads) {
            try {
                thread.join(10_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        threads.clear();
        List<ReconciliationJob> unexecuted = new ArrayList<>();
        queue.drainTo(unexecuted);
        if (!unexecuted.isEmpty()) {
            jobRepository.release(unexecuted.stream().map(ReconciliationJob::id).toList());
            log.info("Released {} queued job(s) back to PENDING", unexecuted.size());
        }
        log.info("Reconciliation worker stopped");
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    /** Started explicitly by the application (after the startup registration run), not by the context. */
    @Override
    public boolean isAutoStartup() {
        return false;
    }

    /** Keeps the queue filled: claims a new batch whenever no more than {@code threads} jobs are waiting. */
    private void dispatchLoop() {
        while (running) {
            try {
                if (queue.size() > settings.threads()) {
                    Thread.sleep(200);
                    continue;
                }
                List<ReconciliationJob> claimed = jobRepository.claimBatch(settings.claimBatchSize());
                if (claimed.isEmpty()) {
                    Thread.sleep(settings.idlePollInterval());
                    continue;
                }
                queue.addAll(claimed);
                log.debug("Claimed {} job(s), queue size {}", claimed.size(), queue.size());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (RuntimeException e) {
                log.error("Reconciliation dispatcher error (e.g. database unreachable); pausing {}",
                        settings.idlePollInterval(), e);
                if (!pause()) {
                    return;
                }
            }
        }
    }

    /** Runs one chunk per queued job, within the shared rate limit. */
    private void executeLoop() {
        while (running) {
            ReconciliationJob job;
            try {
                job = queue.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            try {
                rateLimiter.acquire();
                executor.execute(job);
            } catch (InterruptedException e) {
                // stopped while waiting for the rate limit: hand the job back
                queue.offer(job);
                Thread.currentThread().interrupt();
                return;
            } catch (RuntimeException e) {
                // executor handles job failures itself; this is e.g. the database being unreachable
                log.error("Reconciliation worker error on {}; pausing {}", job, settings.idlePollInterval(), e);
                if (!pause()) {
                    return;
                }
            }
        }
    }

    private boolean pause() {
        try {
            Thread.sleep(settings.idlePollInterval());
            return true;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static Thread newThread(Runnable task, String name) {
        Thread thread = new Thread(task, name);
        thread.setDaemon(false);
        return thread;
    }
}
