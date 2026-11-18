package com.rmrx.marketnexus.marketdata.service.reconciliation.job;

/** Lifecycle of a reconciliation job (database enum {@code job_status}). */
public enum JobStatus {
    /** Waiting to run (again). */
    PENDING,
    /** Being executed. */
    RUNNING,
    /** Window fully fetched. */
    DONE,
    /** Gave up after too many failed attempts. */
    FAILED,
    /** Window no longer fetchable (older than the exchange keeps candles of this timeframe). */
    SKIPPED
}
