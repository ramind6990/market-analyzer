# market-data: gaps and reconciliation

Package `marketdata.service.reconciliation` (series, gap, job.registration, job.execution). Repairs missing closed
candles of reconciled timeframes (M1, H1) from the exchange REST API.

## Gap detection (no table scans)
Gaps come from tracking (see `tracking.md`). `ReconciliationGapDetectionService` builds candidate gaps after the
series watermark from: range before the oldest known candle (first-run backfill), unfilled `candle_gap` rows, trailing
range after the newest candle (silent feed), up to the last closed slot.

## Registration (`ReconciliationRegistrar.registerAll`)
- `market_series.verification_watermark`: new series seeded at `horizon − 1 tf`; horizon = now − (retention − safety
  margin), aligned to the grid incl. `grid-offset` (Nobitex H1 30m). First run therefore backfills the whole retention.
- Gaps older than the horizon are unrecoverable (logged). Watermark moves to first recoverable gap − tf (only the
  registrar moves it).
- Nearby gaps packed while window span ≤ `max-window-candles` (one big gap stays one window); open jobs subtracted.
- Job rows `reconciliation_job` PENDING, cursor = window start; priority 0 recent (within `recent-window`), 1 non-M1,
  2 M1 backfill.
- Runs at startup. Periodic run (`ReconciliationScheduler`, every `schedule-interval`) exists but its
  `@Configuration/@EnableScheduling` are commented out → today gaps are only registered at startup.

## Execution
- `ReconciliationWorker`: dispatcher claims small batches (`FOR UPDATE SKIP LOCKED`, priority order); executor
  threads share one rate limiter (`requests-per-minute`, 35 for Nobitex).
- `ReconciliationJobExecutor`: one chunk (≤ `fetch-chunk-candles` = 500) per execution from the cursor; horizon
  re-check; `ExchangeCandleFetcher` → `ExchangeClient.getOhlc` (limit = candles in range, closed only); upsert POLL in
  one transaction with cursor advance; then tracker (all received candles) and publish inserted/changed to
  `m1-reconciled` / `h1-reconciled`. Missing exchange data is only logged. Failures: doubling backoff, FAILED after
  `max-attempts`.
- Per-exchange settings: `reconciliation.exchanges.<type>.<TF>.{retention, grid-offset}`.

## Decisions / lessons
- Reconciler sends M1 and H1 for recent gaps (≤ 7 d), H1 only for older ones (Nobitex M1 retention).
- Jobs are DB rows (survive restarts, idempotent, observable); executor never touches the watermark.
- Bug found in testing: Nobitex `to` is exclusive → every chunk lost its last candle; fixed in the Nobitex client.
- Real exchange outages exist (Nobitex M1 2026-11-21 13:41–13:53 has no data): such gaps can never be filled and
  currently block the series for ever (see `improvements.md`).
