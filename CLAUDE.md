# market-nexus

A **market analyzer**: collects crypto candles from one exchange, keeps gap-free history for every
(exchange, symbol, timeframe) series, computes technical indicators on it and publishes the results to Kafka.
Strategies, trading and execution are **not** part of this project; other projects consume its output
(topic `series-indicators-updated`) and own their own contracts.

## Pipeline
```
exchange (WebSocket M1, REST for repair)
  └► market-data: store M1 ─► Flink derives M5..D1 ─► store ─► tracking (complete history per series)
        └► candle-series-tracking ─► market-analysis: seeded indicator jobs ─► DB + series-indicators-updated
```

## Modules
| Module | What |
|---|---|
| `exchange` | plain Java exchange clients (Nobitex, Binance) behind generic REST/stream contracts |
| `market-data` | ingestion, reconciliation, tracking, Flink aggregation; see `market-data/CLAUDE.md` |
| `market-analysis` | indicator engine and jobs; see `market-analysis/CLAUDE.md` |
| `tools` | dev-only helpers (indicator reference comparison with pandas_ta_classic) |

## Tech and conventions
- Java 25, Maven multi-module (groupId `com.rmrx.market-nexus`, packages `com.rmrx.marketnexus.*`), Spring Boot 4,
  Jackson 3 (`tools.jackson`), Flink 2.2, Kafka, PostgreSQL + Flyway, plain JDBC (`JdbcClient`, no JPA), ta4j 0.18.
- Library modules (`exchange`, `market-data-model`) never read env/config; callers pass config objects.
- At-least-once messaging everywhere; correctness via idempotent upserts keyed by
  `(exchange, symbol, timeframe, open_time)`.
- One exchange per deployment: `EXCHANGE_TYPE` = `crypto_nobitex` | `crypto_binance`.
- Local infra: Kafka `localhost:9892`, PostgreSQL `localhost:5495/trading`. Build: `mvn -q install -DskipTests`.
- No tests unless asked; discuss design before larger changes; ask before committing.

## Context
Project reference lives in `.claude/context/`:
- `marketdata/`: overview, m1-ingestion, flink-aggregation, gap-reconciliation, tracking, improvements
- `analysis/`: indicator-computation, job-execution, improvements
