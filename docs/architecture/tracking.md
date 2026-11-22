# Series tracking

Package: `com.rmrx.marketnexus.marketdata.service.tracking` (market-data-service).
Background and design discussion: [new-gap-detection.md](new-gap-detection.md).

## Purpose

Tracking knows, for every series (exchange, symbol, timeframe), which stored closed candles form an unbroken history.
It serves two goals:

1. **Gap detection at write time.** Gaps are noticed the moment a candle is stored, not by a periodic scan of the
   `candle` table. Detected gaps are kept in the `candle_gap` table.
2. **A cheap "series is ready" signal.** The length of the newest unbroken run tells, without any query, whether a
   series has enough complete history (e.g. analysis `minHistory`) right after a candle closes. Every change is
   published to Kafka for analysis.

## Packages

| Sub-package | Classes | Responsibility |
|-------------|---------|----------------|
| `tracker`   | `SeriesTracker`, `SeriesKey`, `TrackingListener` | entry point / mediator |
| `book`      | `TrackingBook`, `InMemoryTrackingBook`, `ContiguousRun`, `MissingRange`, `TrackingEvent` | in-memory runs per series |
| `gap`       | `CandleGap`, `CandleGapRepository`, `JdbcCandleGapRepository` | `candle_gap` table |
| `rebuild`   | `TrackingRebuilder`, `TrackingLookback`, `ContiguousRunQuery` | startup rebuild from the DB |
| `publish`   | `KafkaTrackingPublisher`, `CandleSeriesHistoryChanged`, `CandleSeriesHistoryChangedJson` | Kafka message to analysis |

## Model

- **Run** (`ContiguousRun`): closed candles with nothing missing in between.
- **Book** (`TrackingBook`, impl. `InMemoryTrackingBook`): the runs of every series, sorted. Pure state behind an
  interface, so a shared implementation (e.g. Redis) can replace the in-memory one. Single instance for now.
- **Gap** (`candle_gap` row): written when a candle arrives beyond the next expected one; marked `filled` once the book
  holds all its candles.

What one closed candle does to the book (`TrackingEvent`):

| Candle lands                          | Result                                   |
|---------------------------------------|------------------------------------------|
| right after the newest run            | newest run grows                         |
| further ahead                         | new newest run, gap row written          |
| at the edge / last slot of a hole     | hole shrinks / runs merge, gap filled    |
| before the oldest run                 | backfill, oldest run grows (no gap row)  |
| inside a run                          | duplicate (redelivery, reconnect), ignored |
| off the timeframe grid                | ignored, warning                         |

Only closed candles count; partial derived candles never reach tracking.

## Callers

All writes of closed candles go through `SeriesTracker`, always **after the database commit**, so the book never
claims a candle the database does not hold. Per series, calls are serialized (lock per series).

| Caller | When | Why |
|--------|------|-----|
| `M1IngestionService` (live M1) | after upsert, before publishing to `m1-live` | the live edge of every M1 series; reveals gaps when the feed skips candles. Each symbol is handled by one fixed worker thread, so candles of a series arrive in order. |
| `DerivedCandleIngestionService.ingest` (M5..W1 from Flink) | after the batch upsert, closed candles grouped per series | derived series are tracked like any other; the newest run of e.g. H4 tells if it is ready. |
| `ReconciliationJobExecutor` (M1, H1) | after the chunk commit, before publishing to `m1/h1-reconciled` | reconciled candles fill holes and merge runs; all received candles are passed (known ones are duplicates), which also repairs anything the book missed. |

Startup (`MarketDataServiceApplication`): `TrackingRebuilder` loads the book from the database first (gaps-and-islands
query `ContiguousRunQuery`), then reconciliation registration, worker and stream start. Lookback per series:
`market-data.tracking.rebuild-candles`, extended back to the reconciliation watermark (`TrackingLookback`, implemented
by `WatermarkLookback`) and to the oldest unfilled gap. Downtime shows up by itself: the first candle after restart lands
beyond the newest run and opens a gap.

## Use by reconciliation

The registrar no longer scans candles. `ReconciliationGapDetectionService` builds the gaps after each watermark from:

1. the range before the oldest candle the book knows (e.g. first-run backfill),
2. unfilled `candle_gap` rows,
3. the trailing range after the newest candle (silent feed).

Watermark, clipping, packing and job registration are unchanged. Reconciled timeframes must also be tracked.

## Mediator to analysis

`SeriesTracker` sits between the producers of candles (ingestion, derived ingestion, reconciliation) and the
consumers of series state (analysis, via Kafka). Producers only report "this candle is stored"; they know nothing
about consumers. Consumers only learn "the complete history of this series changed"; they know nothing about where
candles come from.

```
M1 ingestion ─┐
Derived       ├─► SeriesTracker ──► TrackingBook (runs)
Reconciliation┘        │       └──► candle_gap (gaps)
                       ▼
             TrackingListener.onCompleteHistoryChanged(series, CompleteHistory)
                       ▼
             KafkaTrackingPublisher ──► topic candle-series-tracking ──► analysis
```

- **Complete history** = contiguous candles up to the oldest *blocking* missing range. A missing range blocks when it
  overlaps an unfilled `candle_gap` row (a forward gap: newer candles arrived first). Missing ranges before known history
  (backfill of old data) or without a gap row do not block, so a deep backfill never moves the complete point back.
- `CompleteHistory(lastCompleteOpenTime, completeLength, previousCompleteOpenTime, lastOpenTime)`:
  complete point, length of the run ending there (bounded by the rebuild lookback after a restart), complete point of
  the previous notification (`null` after a start), newest candle at all.
- The tracker calls `TrackingListener` when the complete point or the complete run's length changed (once per call;
  a reconciliation chunk gives one call with the final state), and once per series after the startup rebuild.
  Not while only candles after an open gap arrive. Example, M1 with gap 10:05-10:07: 10:04 -> notified
  (complete 10:04); 10:08..10:10 -> nothing; gap filled -> notified (previous 10:04, complete 10:10, last 10:10).
- `KafkaTrackingPublisher` implements the listener and sends a `CandleSeriesHistoryChanged` message:
  - topic `market-data.kafka.topics.candle-series-tracking` (default `candle-series-tracking`);
  - key `exchange:symbol:timeframe` (messages of one series stay in order);
  - value JSON `{exchange, symbol, timeframe, lastCompleteOpenTime, completeLength, previousCompleteOpenTime,
    lastOpenTime}`.
- Deciding whether a series is ready (`completeLength >= warmup`) is up to the consumer. The model lives in
  market-data-service; analysis keeps its own copy (a shared contract module only if shared models grow).
- The listener is an interface owned by tracking, so more consumers can be added without touching the tracker. It
  runs inside the series lock: it must be fast and must not block (the Kafka send is asynchronous).
- Failures in tracking or in a listener are logged and swallowed; they never stop storing or publishing candles.

## Settings

```yaml
market-data:
  tracking:
    rebuild-candles: 1000   # startup lookback per series, in candles of its timeframe
    # timeframes: [...]     # tracked timeframes; default all
```

## Limits

- Single instance: the book lives in memory of one market-data-service.
- Old runs are not pruned while the service runs (they only add up while holes stay unfilled).
