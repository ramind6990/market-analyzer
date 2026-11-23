# market-analysis

Computes technical indicators per series and publishes them. Triggered by `candle-series-tracking`
(complete history changed); runs one stateless but **seeded** job per series (stored node values incl. hidden
recursive state let a job continue from the last stored bar instead of recomputing history).

## Layout (package `com.rmrx.marketanalyzer.marketanalysis`)
- `technical.indicator`: engine (spec, schema, definition, `definition.type` = ta4j-based, `definition.custom` =
  self-built, block, catalog, graph, compute, preset). v1 indicator set = presets only (102 types, 108 presets).
- `seriestracker`: Kafka listener + own copy of `CandleSeriesHistoryChanged`.
- `job` (`schedule`, `compute`, `publish`): priority queue scheduler, input loader (SEEDED/FULL), job, publisher.
- `store`: candle reader (market-data `public.candle`), indicator rows / layouts (schema `analysis`, Flyway).
- `service`, `api`: read access to stored indicators (`IndicatorController`).

## Output
Table `analysis.indicator_value` (one `float8[]` per bar, names in `indicator_layout`) and topic
`series-indicators-updated` (key `exchange:symbol:timeframe`, mode LIVE/BACKFILL, public values of the newest bar).

## Rules
- ta4j is the implementation reference; never use recursive ta4j classes directly (port onto seeded blocks).
- Formula change of an existing type → bump `IndicatorDefinition.version()` (new layout, recompute).
- Flat/zero inputs must give 0/neutral, never NaN (NaN poisons recursive state for ever).
- TA-Lib / pandas_ta_classic only for comparing custom types (test scope, `tools/indicator-reference`).

## Context (read the one matching your task)
- `../.claude/context/analysis/indicator-computation.md`: engine, seeding, implemented types, what is missing and why
- `../.claude/context/analysis/job-execution.md`: trigger, scheduler/queue, SEEDED/FULL, storage, published event
- `../.claude/context/analysis/improvements.md`: scaling for user specs, open points from the test run
