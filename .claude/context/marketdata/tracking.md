# market-data: series tracking

Package `marketdata.service.tracking` (tracker, book, gap, rebuild, publish).

## Purpose
1. Gap detection at write time (replaced the periodic gap SQL scan).
2. Cheap "series ready" signal for analysis: length of the complete run, published on every change.

## Model
- Run (`ContiguousRun`): closed candles without holes. `TrackingBook` (in-memory impl, Redis later) holds runs per
  series `(exchange, symbol, timeframe)`.
- A candle landing beyond the next expected slot opens a gap → `candle_gap` row; the row is marked filled only when
  the book holds **all** its candles. Backfill before the oldest run grows it without a gap row; duplicates and
  off-grid candles are ignored.
- What one closed candle does: right after newest run → run grows; further ahead → new run + gap row; at edge/last
  slot of a hole → hole shrinks / runs merge, gap filled; before oldest run → backfill; inside a run → duplicate;
  off grid → ignored with warning.
- **Complete history** = runs up to the oldest *blocking* missing range (one overlapping an unfilled gap row).
  Backfill holes without a gap row never block, so deep backfill never moves the complete point back.

## Callers (all after DB commit, serialized per series)
M1 ingestion, derived ingestion (Flink output, closed only), reconciliation executor (every received candle, which also
repairs anything the book missed). `SeriesTracker` is the only entry point (mediator): producers know nothing about
consumers.

## Output to analysis
`TrackingListener` → `KafkaTrackingPublisher` → topic `candle-series-tracking`, key `exchange:symbol:timeframe`,
JSON `CandleSeriesHistoryChanged{lastCompleteOpenTime, completeLength, previousCompleteOpenTime, lastOpenTime}`.
Sent when the complete point or length changes, and once per series after startup rebuild. Not sent while only
candles after an open gap arrive. "Ready" (`completeLength >= warmup`) is decided by the consumer. Analysis keeps its
own copy of the record (keep in sync; no shared contract module by choice).

Example (M1, gap 10:05–10:07): 10:04 → notified (complete 10:04); 10:08..10:10 → nothing; gap filled → notified
(previous 10:04, complete 10:10, last 10:10).

## Startup
`TrackingRebuilder` loads runs from the DB first (gaps-and-islands `ContiguousRunQuery`, lookback `rebuild-candles`,
extended to the reconciliation watermark and the oldest unfilled gap), then registration, worker, stream.

## Limits
Single instance (book in memory); old runs not pruned while running; listener runs inside the series lock (must not
block; Kafka send is async); failures are logged, never stop candle storage. A gap row containing an unfillable hole
stays unfilled for ever, so restarts re-register (and refetch) its whole range.
