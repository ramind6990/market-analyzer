-- Order in which due jobs are executed: 0 = high (recent gaps), 1 = medium (H1 and other larger timeframes),
-- 2 = low (backfill of older history).
ALTER TABLE reconciliation_job ADD COLUMN priority smallint NOT NULL DEFAULT 2;

-- Due jobs in execution order: used by the worker's claim query.
CREATE INDEX reconciliation_job_due ON reconciliation_job (priority, next_run_at) WHERE status = 'PENDING';
