package com.rmrx.marketanalyzer.marketdata.service.reconciliation.job;

import com.rmrx.marketanalyzer.marketdata.model.Timeframe;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.ToIntFunction;

/** Storage of reconciliation jobs ({@code reconciliation_job}). */
public interface ReconciliationJobRepository {

    /** Windows of open (PENDING or RUNNING) jobs per symbol, ordered by start; symbols without open jobs are absent. */
    Map<String, List<FetchWindow>> findOpenWindows(String exchange, Timeframe timeframe, List<String> symbols);

    /** Registers one PENDING job per window, with its cursor at the window start and the given priority. */
    void insertAll(String exchange, String symbol, Timeframe timeframe, List<FetchWindow> windows,
                   ToIntFunction<FetchWindow> priority);

    /**
     * Claims the next due job (PENDING, next_run_at reached; lowest priority value first, then oldest due) and marks it
     * RUNNING in the same statement. Safe with several threads/instances ({@code FOR UPDATE SKIP LOCKED}).
     */
    default Optional<ReconciliationJob> claimNext() {
        return claimBatch(1).stream().findFirst();
    }

    /**
     * Claims up to {@code limit} due jobs at once (same order as {@link #claimNext()}) and marks them RUNNING.
     *
     * @return the claimed jobs, in execution order
     */
    List<ReconciliationJob> claimBatch(int limit);

    /** Puts claimed but not executed jobs back to PENDING (e.g. still queued at shutdown). */
    void release(List<Long> ids);

    /** Puts jobs left RUNNING (e.g. by a crash) back to PENDING; returns how many. */
    int resetRunning();

    Optional<ReconciliationJob> findById(long id);

    /** Marks the job as being executed. */
    void markRunning(long id);

    /** Marks the job SKIPPED (nothing left to fetch, e.g. window older than retention), with the reason. */
    void skip(long id, String reason);

    /** After a successful chunk: moves the cursor and sets the status (PENDING if more to fetch, DONE if complete). */
    void advance(long id, Instant cursor, JobStatus status);

    /** After a failed execution: PENDING with a later {@code nextRunAt}, or FAILED once out of attempts. */
    void recordFailure(long id, JobStatus status, int attempts, Instant nextRunAt, String error);
}
