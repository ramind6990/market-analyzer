# market-data: overview

Collects candles of one exchange, stores them, derives higher timeframes and tells analysis which series have
complete history.

## Modules
| Module | Kind | Role |
|---|---|---|
| `exchange` (root) | plain Java lib | exchange clients behind generic contracts: `ExchangeClient` (REST OHLC), `ExchangeStreamClient` (WebSocket candles); `crypto.nobitex`, `crypto.binance`. Reads no config; caller passes `ExchangeConfig`. Created via `ExchangeClients` (switch on `ExchangeType`). |
| `market-data-model` | plain Java lib | `Candle`, `Timeframe`, `CandleJson` (shared with analysis) |
| `market-data-service` | Spring Boot app | M1 ingestion, reconciliation, tracking, stores derived candles from Flink |
| `market-data-aggregator` | Flink job | cascade M1 → M5 … D1 (`CandleAggregationJob`) |

## Data flow
```
exchange WS ─► M1 ingestion ─► candle (DB) ─► m1-live ─────────┐
exchange REST ─► reconciliation ─► candle ─► m1/h1-reconciled ─┴► Flink ─► candles-derived ─► candle (DB)
every stored closed candle ─► SeriesTracker ─► candle_gap + candle-series-tracking ─► analysis
```

## Key decisions
- One exchange per deployment: `market-data.ingestion.exchange-type` = `crypto_nobitex` | `crypto_binance`
  (env `EXCHANGE_TYPE`). Per-exchange settings (URLs, reconciliation retention / grid offset) under `exchanges.<type>`.
- Live data = WebSocket M1 only; REST is the authority, used for reconciliation. Higher timeframes are derived, never
  streamed (except REST H1 during reconciliation).
- Storage: PostgreSQL, plain JDBC (`JdbcClient`), Flyway, Postgres enums (`exchange_type`, `candle_source`
  STREAM/POLL/DERIVED). Idempotent upserts keyed `(exchange, symbol, timeframe, open_time)`.
- Kafka + Flink (chosen over Kafka Streams); at-least-once everywhere, idempotency via upserts.
- Library modules never read env/config.
- Deep history (beyond exchange retention) comes from a separate script, not the app.

## Principles (kept from the first design)
- PostgreSQL is the source of truth; Kafka messages are wake-up calls carrying data, never the truth.
- Only closed candles are stored as final and published downstream; decisions are made on closed candles only.
- Collect broadly and continuously: minute data is kept only ~7 days by Nobitex and cannot be recovered later.
- Prefer one quote currency (USDT): IRT pairs mix coin moves with the rial rate.
- Aggregation rule verified against Nobitex: M1 → H1 skipping v=0 filler candles reproduces native H1 exactly
  (naive aggregation gets the open wrong); M5 volumes are rounded to 8 decimals → compare volume with tolerance.
- Corrections of already published candles: detect/log only; a correction event + recompute only if seen in practice.
- History: the first design was REST-only polling with `published_until` watermarks and subscription statuses
  (WARMING_UP/LIVE/STALE); replaced by WebSocket + Flink + tracking (`candle-series-tracking`, consumer decides
  readiness).

## Exchange facts that shape the design
- Nobitex: REST max 500 candles/call, ~60 req/min, `to` is **exclusive** (client sends `to + 1 s`); M1–M30 kept
  ~7 days, H1 since 2018; hours start at :30, days at 20:30 UTC; no-trade minutes come as filler candles (v=0);
  WS pushes snapshots ~10 s, no closed flag.
- Nobitex REST: 429 carries `backOff` seconds; one symbol per call; `/market/stats` gives 24 h stats of all markets
  in one call (market scan). Private API: Ed25519 signature headers.
- Binance: REST klines max 1000, UTC grid, WS kline has closed flag `x`; not reachable from the dev network, so the
  client is written from docs and untested.

## Kafka topics
`m1-live`, `m1-reconciled`, `h1-reconciled` (key `exchange:symbol`) → Flink → `candles-derived`
(key `exchange:symbol:timeframe`); `candle-series-tracking` → analysis.
