# market-data

Gets candles of one exchange into PostgreSQL and keeps every series complete:
live M1 via WebSocket, repair via REST, higher timeframes derived by Flink, and a tracking signal telling analysis how
much gap-free history each series has.

## Submodules
| Module | Kind | Role |
|---|---|---|
| `market-data-model` | plain lib | `Candle`, `Timeframe`, `CandleJson` (also used by market-analysis) |
| `market-data-service` | Spring Boot | M1 ingestion, reconciliation (gap jobs over REST), tracking, stores Flink output |
| `market-data-aggregator` | Flink job | cascade M1 → M5 → M15 → M30 → H1 → H4 → H12 → D1 (`CandleAggregationJob`) |

Uses the root `exchange` module for exchange access (`ExchangeClients.create` / `createStream`).

## Kafka
In: `candles-derived` (from Flink). Out: `m1-live`, `m1-reconciled`, `h1-reconciled` (to Flink),
`candle-series-tracking` (to analysis).

## Running
Start the Flink job first (`CandleAggregationJob`, broker `localhost:9892`), then `market-data-service`
(`EXCHANGE_TYPE=crypto_nobitex|crypto_binance`). On a fresh DB with old topic data, move consumer group offsets to
the topic ends first, or old candles are replayed.

## Context (read the one matching your task)
- `../.claude/context/marketdata/overview.md`: modules, data flow, decisions, exchange facts
- `../.claude/context/marketdata/m1-ingestion.md`: live M1 ingestion
- `../.claude/context/marketdata/flink-aggregation.md`: derived candles, count-complete windows, grid
- `../.claude/context/marketdata/gap-reconciliation.md`: gap jobs, watermark, REST fetch
- `../.claude/context/marketdata/tracking.md`: runs, `candle_gap`, complete history, message to analysis
- `../.claude/context/marketdata/improvements.md`: open points (accepted gaps, scheduler, …)
