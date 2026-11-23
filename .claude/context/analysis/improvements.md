# analysis: further improvements

## Scaling for user-defined indicator specs (later; v1 = presets only)
- Decided: analysis only computes and publishes values; strategies subscribe and apply their own rules.
- Problem with arbitrary user params: one `node_values` array per series = union of all specs → every subscribe /
  unsubscribe changes the layout → FULL recompute of all nodes; array grows per distinct spec; one heavy spec raises
  warm-up/history for the whole series.
- Direction: subscription registry per series (spec, refcount; presets = system subscriptions); store per node
  (series, node hash, open_time, values[]) so adding a spec backfills only its nodes (alternative: hybrid, presets
  wide array + user specs per node).
- Capacity model: cost scales with unique (series × node), not users.
  `job_cost = fixed_overhead + Σ node_weight × new_bars`; burst at timeframe boundaries (:00 hour = 5 TFs, day = all);
  constraint `burst_drain_time < deadline`. Node weight differs (recursive O(1), window O(window), RWI O(period)).
- Limits to set from the start: per-series cost ceiling (one series = one core, hard limit), per-user quota,
  admission check on subscribe, backfill throttling (BACKFILL priority, capped), per-instance capacity.
- First steps: measure per-type seeded +1 bar cost, measure fixed job overhead with real DB, then put numbers on limits.

## Found in the test run (2026-11-23)
- **Unfillable gaps** block series for ever → no LIVE ever (needs market-data accepted-gap policy).
- **H1 and higher never compute**: available history (H1 30 d = 720) < `maxWarmup` 1,020. Deeper history or a
  per-timeframe warm-up/preset choice.
- **Silent skip** while `completeLength < maxWarmup`: log it (series looked idle for 15 min without any line).
- PPO/PVO NaN fix applied without definition version bump → stored NaN seeds of the current layout stay NaN until a
  recompute. Add `version` support to `DefinitionBuilder` and bump PVO when deploying to non-test data.

## Operations
- Startup sweep: offsets are committed before the job runs; a crash loses that trigger until the next candle.
- Retention cleanup of `indicator_value` (keep ~1,000 bars per series) and of old layouts.
- Metrics: job duration, queue wait, burst drain time (only log lines today).
- Decide per timeframe which presets really run (230 values per bar, full compute ~55–90 ms).
- Keep `CandleSeriesHistoryChanged` copy in sync with market-data.

## Indicator backlog
Deferred types and why: see `indicator-computation.md` (model-dependent, not-per-bar, non-OHLCV sources).
