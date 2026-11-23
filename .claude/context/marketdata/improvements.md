# market-data: further improvements

Open points found in design and in the end-to-end test run (2026-11-23, real Nobitex data).

## Must-have
- **Accepted (unfillable) gaps.** Exchanges have real outages (Nobitex M1 2026-11-21 13:41–13:53 returns no data).
  Today such a gap blocks the complete history for ever: analysis stops at the gap, no LIVE updates. Needed: after
  the exchange confirmed no data (e.g. N attempts / job DONE with missing range), mark the range accepted (e.g.
  `candle_gap.accepted` or synthetic flat filler candles at previous close, `source` marked) so tracking treats it as
  non-blocking; Flink windows need the same (count-complete windows never close otherwise).
- **Enable periodic reconciliation registration** (`ReconciliationScheduler` annotations are commented out). Without
  it gaps are only registered at startup.
- **Split partly filled gap rows.** A gap row is filled only when complete; a large row with one unfillable hole makes
  every restart refetch the whole range. Shrink/split rows as candles arrive (or rely on accepted gaps).

## Should
- **Kafka hygiene on fresh environments**: Flink and service groups start from EARLIEST when no offsets are committed
  → old topic data is replayed into a purged DB. Document/automate offset reset or use per-environment topics.
- **Deeper H1 history**: H1 lookback is 30 d (720 bars) < analysis warm-up 1,020 → H1 and higher never get indicators.
  Extend H1 retention for first run (Nobitex has H1 since 2018) or load via the history script.
- Optional periodic REST verification pass to correct WS candles that missed last-second trades (Nobitex).
- Binance client: first live test (network blocks Binance; use VPN or `*.binance.vision` URLs).

## Later
- Tracking book in Redis (multi-instance), prune old runs.
- Retention of old candles (TimescaleDB compression/retention planned).
- Data from trades / order book (CVD, volume delta, spread, depth imbalance): new streams recorded live (no history),
  aggregated per bar on ingest and exposed as new sources. Nobitex trades WS has no trade IDs → unverifiable.
- Shared contract module for events only if shared models grow.
