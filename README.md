# market-analyzer

A crypto **market analyzer**. It collects candles from an exchange, keeps a gap-free history for every
`(exchange, symbol, timeframe)` series, computes technical indicators on it and publishes the results to Kafka.

Trading, strategies and order execution are out of scope: other projects consume the published indicator updates
and make their own decisions.

```
exchange ── WebSocket M1 (live) ──┐
         └─ REST (repair gaps) ───┤
                                  ▼
                           market-data ── M1 ──► Flink ── M5 … D1 ──► market-data (PostgreSQL)
                                  │
                                  └─ candle-series-tracking (complete history per series)
                                                 ▼
                                          market-analysis ── indicators ──► PostgreSQL
                                                 │
                                                 └─ series-indicators-updated ──► your project
```

## Features
- Live M1 candles over WebSocket, REST as the authority for repair.
- Exchanges: **Binance** and **Nobitex**. One exchange per deployment.
- Higher timeframes (M5, M15, M30, H1, H4, H12, D1) derived by a Flink cascade with count-complete windows, aligned to
  the exchange's candle grid.
- Gap detection at write time and automatic reconciliation jobs (rate limited, retried, persisted).
- 102 indicator types / 108 presets (moving averages, trend, momentum, volatility, volume, statistics, candlestick
  patterns), based on ta4j plus self-built types.
- Seeded, stateless indicator jobs: each job continues from the stored state of the last bar instead of recomputing
  history (+1 bar ≈ 0.3 ms for all presets).

## Modules
| Module | Kind | What it does |
|---|---|---|
| `exchange` | Java library | Exchange clients behind generic contracts: REST OHLC (`ExchangeClient`) and WebSocket candle streams (`ExchangeStreamClient`); Nobitex and Binance. Reads no configuration itself. |
| `market-data/market-data-model` | Java library | Shared candle model (`Candle`, `Timeframe`, JSON). |
| `market-data/market-data-service` | Spring Boot app | Live M1 ingestion, reconciliation of gaps over REST, series tracking (`candle_gap`, complete-history events), stores derived candles from Flink. |
| `market-data/market-data-aggregator` | Flink job | Cascade M1 → M5 → M15 → M30 → H1 → H4 → H12 → D1 (`CandleAggregationJob`). |
| `market-analysis` | Spring Boot app | Indicator engine, per-series job scheduler, indicator storage |

Design notes for each area are in `.claude/context/` (marketdata/ and analysis/).

## Tech stack
Java 25 (the Flink job targets Java 21), Maven, Spring Boot 4, Apache Flink 2.2, Apache Kafka 4 (KRaft),
PostgreSQL 17 + Flyway, plain JDBC (`JdbcClient`), Jackson 3, ta4j 0.18.

## Getting started

### Prerequisites
- JDK 25 and Maven 3.9+
- Docker with Docker Compose

### 1. Build
```bash
mvn clean install            # builds all modules and runs the indicator tests
```

### 2. Infrastructure (PostgreSQL, Kafka, Flink)
```bash
cp .infra/.env.example .infra/.env
# edit .infra/.env: set POSTGRES_PASSWORD and POSTGRES_PORT=5495 (the apps default to localhost:5495)

docker compose -f .infra/docker-compose-postgres.yml  --env-file .infra/.env up -d
docker compose -f .infra/docker-compose-messaging.yaml --env-file .infra/.env up -d
```
| Service | Address |
|---|---|
| PostgreSQL | `localhost:5495`, database `trading` |
| Kafka | `localhost:9892` from the host, `market-analyzer-kafka:9092` inside the compose network |
| Flink UI | http://localhost:8381 |

The apps read DB settings from `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` (defaults:
`jdbc:postgresql://localhost:5495/trading`, `trading` / `trading_pass`) and Kafka from `KAFKA_BOOTSTRAP_SERVERS`.

### 3. Flink aggregation job (start first)
```bash
.infra/deploy-flink-aggregator.sh            # build, copy jar, (stop with savepoint), flink run
.infra/deploy-flink-aggregator.sh --no-build # deploy the jar already built
.infra/deploy-flink-aggregator.sh --fresh    # restart without state
```
The script copies the shaded jar to `.infra/data/flink/target` (mounted as `/opt/flink/usrlib`), stops a running
`candle-aggregation` job with a savepoint and resumes from it.

### 4. market-data-service
```bash
cd market-data/market-data-service
EXCHANGE_TYPE=crypto_nobitex mvn spring-boot:run     # or crypto_binance
```
Symbols, endpoints and reconciliation settings: `src/main/resources/application.yml`
(`market-data.ingestion.*`, `market-data.reconciliation.*`). Port 8081.

On startup it rebuilds tracking from the database, registers reconciliation jobs (the first run backfills M1 for
7 days and H1 for 30 days), starts the job worker and connects the WebSocket.

### 5. market-analysis
```bash
cd market-analysis
mvn spring-boot:run
```
Port 8082. It consumes `candle-series-tracking`, computes indicators once a series has enough gap-free history
(warm-up 1,020 bars) and publishes to `series-indicators-updated`.

> Fresh database but old data in Kafka? Reset the consumer groups (`candle-aggregator`, `market-data-service`,
> `market-analysis`) to the end of their topics first, otherwise old candles are replayed.

## Consuming the output

### Kafka topic `series-indicators-updated`
- Key: `exchange:symbol:timeframe`, e.g. `CRYPTO_NOBITEX:BTC/USDT:M5`. Messages of one series stay in order.
- Value: JSON (UTF-8), times in ISO-8601:

```json
{
  "exchange": "CRYPTO_NOBITEX",
  "symbol": "BTC/USDT",
  "timeframe": "M5",
  "firstOpenTime": "2026-11-23T10:05:00Z",
  "lastOpenTime": "2026-11-23T10:05:00Z",
  "layoutVersion": -7560615122441903729,
  "mode": "LIVE",
  "values": {
    "RSI(period=14,source=CLOSE).value": 54.21,
    "MACD(fast=12,signal=9,slow=26,source=CLOSE).line": 18.4,
    "MACD(fast=12,signal=9,slow=26,source=CLOSE).signal": 15.9,
    "ATR(period=14).value": 41.7,
    "PVO(fast=12,signal=9,slow=26,source=VOLUME).signal": null
  }
}
```

| Field | Meaning |
|---|---|
| `firstOpenTime`, `lastOpenTime` | bars stored by this job (one bar in live operation, up to thousands during backfill) |
| `mode` | `LIVE`: `lastOpenTime` is the newest closed bar of the series and still fresh: act on it. `BACKFILL`: catch-up, late data or a filled gap: history only, never trade on it. |
| `values` | every public indicator output of the bar at `lastOpenTime`, keyed by its canonical name `TYPE(params).output`; `null` when not a number |
| `layoutVersion` | version of the indicator set the values belong to |

Guidelines for consumers:
- Use your own consumer group and define your own contract class for the JSON (there is no shared contract jar).
- Delivery is at-least-once: deduplicate by `(key, lastOpenTime)`; give side effects (orders, proposals) a unique
  key such as `(strategy, exchange, symbol, timeframe, open_time)`.
- Only the newest bar's values are in the message. For older bars use the REST API or the `analysis` schema.

Minimal Java consumer:
```java
Properties props = new Properties();
props.put("bootstrap.servers", "localhost:9892");
props.put("group.id", "my-strategy");
props.put("key.deserializer", StringDeserializer.class.getName());
props.put("value.deserializer", StringDeserializer.class.getName());
try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
    consumer.subscribe(List.of("series-indicators-updated"));
    while (true) {
        for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofSeconds(1))) {
            JsonNode update = mapper.readTree(record.value());
            if ("LIVE".equals(update.get("mode").asText())) {
                double rsi = update.get("values").get("RSI(period=14,source=CLOSE).value").asDouble();
                // apply your rules
            }
        }
    }
}
```

### REST API (market-analysis)
```
GET http://localhost:8082/api/v1/indicators?exchange=CRYPTO_NOBITEX&symbol=BTC/USDT&timeframe=M5&type=RSI&period=14&limit=100
```
Newest bars first. Every query parameter besides `exchange`, `symbol`, `timeframe`, `type`, `limit` is an indicator
parameter (omitted ones take their defaults). Without `type`: all public indicators of the series.

### Database
Schema `analysis`: `indicator_value` (one `float8[]` per bar), `indicator_layout` (names of the array entries per
`layout_version`), `indicator_definition` (catalog). Candles are in `public.candle`.

## Testing
```bash
mvn test -pl market-analysis
```
`IndicatorEngineTest` checks all presets against ta4j, seeded vs full computation, and every preset's declared
lookback. `ReferenceComparison` (manual) compares the self-built indicators with TA-Lib and pandas_ta_classic
(`tools/indicator-reference`).

