# market-data: Flink derived-candle aggregation

Module `market-data-aggregator`, job `CandleAggregationJob` (pipeline in `CandlePipeline`).

## Cascade
```
m1-live ───────┐
m1-reconciled ─┴► [M5] ─► [M15] ─► [M30] ─► [H1] ─► [H4] ─► [H12] ─► [D1]
                                              ▲
                          h1-reconciled ──────┘   (REST H1, reconciliation only)
```
Only two entry points: M1 (live + reconciled) and H1 (REST). Each stage reads only the stage below.
Output: `candles-derived` (key `exchange:symbol:timeframe`), stored by market-data-service
(`DerivedCandleIngestionService`, source DERIVED) and then tracked.

## Mechanism
- Keyed by market `exchange:symbol`; per stage `WindowBook` keeps children by openTime (latest wins → idempotent).
- **Count-complete windows**, no watermarks/allowed lateness: a window closes when the number of closed children equals
  `expectedChildren` (M5=5, M15=3, M30=2, H1=2, H4=4, H12=3, D1=2). Works because Nobitex sends filler candles.
- **Progressive output**: every child update emits `closed=false`; completion emits `closed=true`.
- Incomplete windows wait (for reconciliation); purged at `windowStart + 7 days` (processing-time timer), logged
  "never completed". Late children for a closed window are dropped (closed starts kept with TTL ~2 days).
- **H1 special**: REST H1 is authoritative → emitted as final, replaces the waiting window, hour marked finalized;
  reconciled M30 is ignored at H1 (relies on the reconciler sending H1 for every hour with a gap). REST H1 is not
  re-published (already stored by reconciliation).
- Merge (`CandleMerger`): open = first child with v>0, high/low over v>0, close = last child with v>0, volume = sum;
  all v=0 → flat candle at previous close. `reconciled=true` if any child was reconciled.
- **Grid per exchange** (`Timeframe.windowStart(exchange, time)`): Nobitex anchor 20:30 UTC (offset = 20:30 mod
  duration → H1/H4 at :30, H12 08:30, D1 20:30); other exchanges (Binance) round UTC.

## Runtime decisions
- Checkpointing on (incomplete windows survive restarts); Kafka sink at-least-once; downstream upserts.
- Sources start from committed offsets, else EARLIEST. Offsets are only committed on checkpoint: a fresh run against
  topics with old data replays it (seen in testing; reset group offsets first).
- Local run: plain `java` with the test-scope classpath (Flink deps are `provided`) and `--add-opens` for Java 25.
