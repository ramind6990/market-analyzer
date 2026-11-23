# analysis: job execution, queue and publish

Packages `marketanalysis.seriestracker`, `job`, `job.schedule`, `job.compute`, `job.publish`, `store`.

## Trigger
- `CandleSeriesHistoryListener` consumes `candle-series-tracking` (group `market-analysis`, JSON via own copy of
  `CandleSeriesHistoryChanged`), builds a `SeriesTrigger` and submits it; offsets are committed before the job runs.
- Analysis computes only up to `lastCompleteOpenTime` (contiguous history). Bars after an open gap are computed for the
  first time once the gap is filled (never overwritten).

## Scheduler (`SeriesJobScheduler`)
- One job per series per trigger, stateless; fixed thread pool + `PriorityBlockingQueue`.
- Priority (`JobPriority`): LIVE first, earliest deadline (bar close + 1 tf), smaller timeframe, arrival.
- Per series at most one running + one queued job; triggers during a run are merged (`SeriesTrigger.merge`) into one
  rerun; a queued BACKFILL is upgraded when a LIVE trigger arrives. Capped jobs requeue themselves.
- Horizontal scale: Kafka partitions by series key → more instances. Overlap on rebalance is harmless (idempotent
  upserts).

## Job (`SeriesIndicatorJob`)
1. `JobInputLoader` decides from **stored rows**, not the trigger:
   - SEEDED: newest `historyBars` contiguous rows + candles up to the complete point, at most `max-new-bars` (5,000).
   - FULL (no/invalid seed, layout change): newest `min(completeLength, maxWarmup + initial-bars)` candles; first
     `maxWarmup` (1,020) rows dropped. No compute (silently) while `completeLength < maxWarmup`.
   - Nothing new → no-op (duplicate trigger).
2. Compute, verify planned mode == computed mode, upsert rows (`IndicatorRowWriter`, one transaction, batches of 500).
3. Mode: LIVE only if not capped, newest stored bar == complete point == newest bar of series, still within deadline;
   else BACKFILL.
4. Publish `SeriesIndicatorsUpdated` (topic `series-indicators-updated`, key `exchange:symbol:timeframe`, JSON):
   series, first/last stored bar, layout version, mode, `values` = public outputs of the **newest bar** (name → value,
   NaN → null). Only the newest bar because a backfill job can store 5,000 bars (~20 MB > Kafka 1 MB); history is in
   the DB / REST API (`IndicatorController`, `IndicatorService`).

## Storage
- `analysis.indicator_value(exchange, symbol, timeframe, open_time, layout_version, node_values float8[])`, PK incl.
  layout. One array per bar: a job always reads/writes all nodes together (row per output ≈ 150x rows).
- `indicator_layout(layout_version, entries[], internal[])`: one list (graph layout) drives stored names, written values
  and seeds read back. Layout change → no seed → FULL into new rows; old rows stay (until retention).
- `indicator_definition`: descriptive catalog copy per formula version. Flyway schema `analysis`.

## Contract for consumers (strategy, other projects)
- Act only on `mode = LIVE`: the newest, fresh bar. BACKFILL (catch-up, late data, gap fill) updates history but must
  never trade.
- Messages are at-least-once; consumers dedupe by (series, lastOpenTime). Side effects (proposals/orders) need a unique
  key, e.g. (strategy, exchange, symbol, timeframe, open_time).
- Strategies declare market(s), timeframe(s) and indicators; the timeframe belongs to the series, not the indicator.
  Strategy definitions are data, so hand-written and agent-proposed strategies take the same path
  (backtest → paper → approval → live); agents propose, never bypass risk.
- Consumers own their contracts (copy the JSON shape); no shared contract module.

## Verified (test run 2026-11-23)
FULL → SEEDED chain correct; message values == DB values exactly (5,820 checked); each message's range fully stored.
Jobs ~100–700 ms for 400–3,300 bars.
