# Flink candle aggregation and reconciliation (design notes)

Saved 2026-11-16 from the design discussion. Supersedes the REST-only ingestion in `marketdata-analysis-architecture.pdf` for live data: WebSocket M1 + Flink cascade, REST only for reconciliation.


## Direction (user decisions, 2026-11-16)
- Live M1 candles for ~200 coins come from the Nobitex WebSocket candle channel; REST is used only to reconcile.
- Stream processing with Apache Flink (chosen over Kafka Streams); Kafka topics as Flink sources/sinks; Postgres is a sink (upserts).
- Higher timeframes are derived in a cascade: M1 → M5 → M15 → M30 → H1 → H4 → H12 → D1.
- Only two entry points: **M1** (live + reconciled) and **H1** (reconciled from REST only). All other stages take input only from the stage below.
- Reconciler detects gaps (e.g. M1 up to 00:20, then from 00:23 → 00:21–00:22 missing), registers jobs, fetches via REST:
  - gap within last 7 days → reconciled **M1** for the missing minutes **and** **H1** for affected hours;
  - gap older than 7 days (Nobitex keeps M1 only 7 days) → **H1 only**.
- Windows are NOT time-closed by watermark/allowed lateness: a window emits only when complete (count of children; Nobitex sends
  filler candles for no-trade minutes, so complete windows have an exact count). Incomplete windows wait until completed by
  reconciled data or purged.
- REST H1 is authoritative: it is emitted as final, purges the waiting H1 window, marks the hour `finalized` (TTL); late M30 children
  for a finalized hour are ignored.
- Minute stages (M5/M15/M30) drop incomplete windows after `windowStart + 7 days` (timer) and log "never completed" — no control
  messages needed. H1 stage needs no timer as long as the reconciler always sends H1 for every hour with a gap.
- Accepted/optional: gap-only reconciliation does not catch WS snapshot staleness (missed last-second trade, e.g. 04:55 test);
  optional periodic REST verification pass if exactness on M1/M5 matters.

## Final code snippet
```
m1-live ───────┐
m1-reconciled ─┴► [M5] ─► [M15] ─► [M30] ─► [H1] ─► [H4] ─► [H12] ─► [D1]
                                              ▲
                          h1-reconciled ──────┘   (REST, only during reconciliation)
```
```java
DataStream<Candle> m1  = kafka(env, "m1-live").union(kafka(env, "m1-reconciled"));

DataStream<Candle> m5  = derive(m1,  "M5",  5);                        // children only
DataStream<Candle> m15 = derive(m5,  "M15", 3);
DataStream<Candle> m30 = derive(m15, "M30", 2);
DataStream<Candle> h1  = deriveWithDirect(m30, kafka(env, "h1-reconciled"), "H1", 2);
DataStream<Candle> h4  = derive(h1,  "H4",  4);                        // REST H1 flows on like any H1
DataStream<Candle> h12 = derive(h4,  "H12", 3);
DataStream<Candle> d1  = derive(h12, "D1",  2);

static DataStream<Candle> derive(DataStream<Candle> children, String tf, int expected) {
    return children.keyBy(Candle::symbol).process(new ChildAggregator(tf, expected));
}
static DataStream<Candle> deriveWithDirect(DataStream<Candle> children, DataStream<Candle> direct,
                                           String tf, int expected) {
    return children.keyBy(Candle::symbol)
                   .connect(direct.keyBy(Candle::symbol))
                   .process(new ChildAggregatorWithDirect(tf, expected));
}
```
```java
// ChildAggregator (M5, M15, M30, H4, H12, D1) — KeyedProcessFunction<String, Candle, Candle>
MapState<Long /*windowStart*/, Accumulator> open;
processElement(child):
    start = windowStart(child.openTime())            // aligned to exchange boundaries (Nobitex :30 / 20:30 UTC)
    acc = open.get(start) ?: new Accumulator(); acc.add(child)   // keyed by child openTime → idempotent
    if (acc.size() == expected) { out.collect(acc.toCandle()); open.remove(start); }
    else { open.put(start, acc); /* minute stages only: timer at start + 7 days → drop + log */ }

// ChildAggregatorWithDirect (H1) — KeyedCoProcessFunction<String, Candle, Candle, Candle>
processElement1(child):                              // M30 from the cascade
    if (finalized.contains(start)) return;           // hour already final from REST
    ...same count logic as above...
processElement2(restH1):                             // direct REST H1
    out.collect(restH1);                             // final, reconciled = true → flows on to H4
    open.remove(start);
    finalized.put(start, true);                      // state TTL e.g. 2 days

// Accumulator: TreeMap<Long, Candle> children by openTime; toCandle(): open = first child with v>0,
// high/low = max/min over v>0, close = last child with v>0, volume = sum; all v=0 → flat candle at previous close.
// Checkpointing enabled so incomplete windows survive restarts. Downstream upserts by (symbol, tf, openTime).
```


## Adjustments (2026-11-19, implementation session)
- **M1 input is closed-only**: ingestion decides when an M1 is closed (next minute's openTime arrives) and only then
  publishes it to `m1-live`. Reconciled M1 (`m1-reconciled`) is closed by definition.
- **Progressive output**: every derived stage (M5..D1) emits on each child update with `closed=false` and once more with
  `closed=true` when the window completes. Output topic `candles-derived`, Kafka key `exchange:symbol:timeframe`;
  downstream upserts by (exchange, symbol, timeframe, openTime).
- **Completion** = number of *closed* children == expected count. Children are stored by openTime (latest version wins),
  so duplicates are idempotent. Several windows per key may be open at once; an incomplete window waits for
  reconciliation. Every stage purges incomplete windows at `windowStart + 7 days` (log "never completed").
- **Keying** by market key `exchange:symbol`, not symbol only.
- **Reconciled flag**: a derived candle has `reconciled=true` if any child was reconciled. Reconciled M1 cascades only up
  to M30 — **H1 ignores reconciled M30**; that hour is completed by the REST H1 (`h1-reconciled`), which is emitted as
  closed + reconciled and flows on to H4 like any H1. This relies on the reconciler sending H1 for every hour with a gap.
  REST H1 is **not** re-published (reconciliation already persisted it): `candles-derived` only carries Flink-derived
  candles.
- **Late children** for an already-closed window (e.g. overlap at the edges of a gap) are dropped and logged; closed
  window starts are kept in state with a TTL (~2 days). No re-emission of closed windows.
- **Flat candle**: if all children have v=0, the candle is flat at the first child's close (filler candles already
  carry the previous close).
- **Grid**: M1–M30 aligned to the epoch grid; H1/H4/H12 aligned to :30 hours, D1 to 20:30 UTC. H4/H12 grid still to be
  verified against Nobitex REST.
- **Runtime**: compiled for Java 21 (Flink 2.2 cluster images run on 17/21), no watermarks (processing-time timers only),
  checkpointing on, Kafka sink at-least-once.
