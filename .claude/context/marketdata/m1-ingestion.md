# market-data: M1 ingestion

Only M1 is ingested live. Every other timeframe is derived from it by Flink (see `flink-aggregation.md`); H1 also
arrives from REST during reconciliation only.

## Flow
1. `IngestionStarter` subscribes `ExchangeTimeframe.M1` for every configured symbol (`BTC/USDT` …) on the
   `ExchangeStreamClient`, then connects.
2. The exchange client reports `onCandleClosed` (forming updates are ignored):
   - Nobitex: no closed flag; a candle counts as closed when the next minute's candle arrives.
   - Binance: kline field `x = true`.
3. `M1IngestionService` (implements `ExchangeCandleListener`): per symbol one fixed worker thread (hash of symbol), so
   candles of a series are handled in order; bounded queue, overflow is dropped and logged.
4. Per candle: upsert (`source = STREAM`, closed) → `SeriesTracker.onClosedCandle` (after commit) → publish to
   `m1-live` only if the row was inserted or changed (no duplicate cascades on reconnect/redelivery).

## Decisions
- WebSocket for live, REST only for repair: WS is cheap for ~150–200 coins; REST stays the authority.
- Closed-only input to Flink: ingestion decides closure, Flink never sees forming M1.
- Accepted weakness (Nobitex): a closed WS candle can miss last-second trades (snapshot every ~10 s); gap-only
  reconciliation does not correct it. Optional later: periodic REST verification pass.
- The stream client always connects live; the former mock streams were removed (testing uses real data).
- Exchange clients reconnect and resubscribe themselves; minutes lost meanwhile become gaps → reconciliation.

## Settings
`market-data.ingestion.*`: `exchange-type`, `exchanges.<type>.{base-url, websocket-url}`, `user-agent`
(Nobitex wants `TraderBot/<name>`), `symbols`, `worker.{threads, queue-capacity}`.
