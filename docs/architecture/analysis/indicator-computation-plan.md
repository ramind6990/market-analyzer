# Plan: Indicator computation in analysis (job per series, seeded ta4j)

## Context
analysis is empty. market-data publishes `CandleSeriesHistoryChanged` (topic `candle-series-tracking`,
key `exchange:symbol:timeframe`). analysis must compute indicators per series and signal a (future)
strategy coordinator. Agreed in discussion:
- Stateless **job per series** on each trigger; no long-lived in-memory series. DB holds candles and the
  last ~1,000 indicator rows per series.
- Benchmark (ta4j 0.18, DoubleNum, 1 series, 1,000 M1 bars, 114 indicators): full compute ~40 ms, newest
  value ~30 ms, +1 bar ~75 us. RWIHigh and ParabolicSar are pathologically slow in ta4j: excluded/rewritten.
- ta4j cannot resume from stored values out of the box -> build indicators from **seedable building blocks**
  and store state for every graph node (hidden ones too), so a job reads only `max window + new bars` candles.
- Indicator set v1 = **presets** only. Strategy subscriptions come later.
- Overrides earlier memory decision "analysis reads ~500 candles per event, stateless": still stateless per
  job, but seeded from stored node values. Update memory `architecture-decisions` after approval.

## 1. market-data event change (small, tracking publisher)
- Event reports the **complete run** (contiguous from the series' start of tracked history up to the first
  open gap), not only the newest run:
  `lastCompleteOpenTime`, `completeLength`, `previousCompleteOpenTime` (complete point at the last event),
  plus existing `lastOpenTime` (newest candle seen at all).
- analysis computes only up to `lastCompleteOpenTime`. Bars after an open gap are not computed until the gap
  is filled; then they are computed for the first time (no overwrite).
- TODO (later): gaps that can never be filled freeze the series; market-data needs an "accepted gap" policy.

## 2. Seedable indicator engine (analysis, package `...analysis.indicator`)
- `IndicatorSpec(type, params)` canonical string; `IndicatorDefinition`: param schema, children specs,
  window size (candles needed besides seed), output names.
- Node graph per series built from presets; shared children (one EMA(26) for MACD and the preset EMA(26)).
  Hidden nodes (EMA legs of MACD, gain/loss averages of RSI, Wilder sums of ADX) are explicit nodes.
- Building blocks as small ta4j subclasses/wrappers that return the **stored value for index <= seedIndex**
  (stops ta4j recursion there): EMA, Wilder/MMA, SMA-based, gain/loss, TR. Composites (RSI, ATR, MACD,
  Bollinger, Stochastic, ADX...) assembled from them instead of ta4j's closed implementations.
- Window-only indicators need no seed, just their window of candles.
- Excluded in v1: ta4j RWIHigh and ParabolicSar classes (slow; replaced by fast own versions, see sections 8 and
  8d), repainting/structure, cross-symbol, anchored indicators. Full list with reasons: section 9.

## 3. Storage (analysis-owned Flyway migration)
- `indicator_value(exchange, symbol, timeframe, open_time, layout_version, values float8[])`,
  PK (exchange, symbol, timeframe, open_time). One row per bar per series, all node values (outputs +
  hidden seed state) in a fixed order given by `layout_version` (hash of the series' node list).
- Layout change (preset set changed) -> stored rows of that series are ignored; series recomputed from candles.
- Upsert by PK (idempotent). Retention: keep last ~1,000 bars per series (periodic delete).

## 4. Job per series
1. Trigger arrives -> scheduler (see 5). Job reads: latest stored row (seed) with its open_time and layout.
2. Decide range: from first uncomputed bar (seed.open_time + 1 tf, or start if no/invalid seed) up to
   `lastCompleteOpenTime`. Nothing new -> no-op (duplicate event).
3. Read candles: `max window` bars before the range start + all bars in range (bounded by
   min(completeLength, 1,000) when no seed).
4. Build node graph seeded at seed index; compute every bar in range; upsert rows.
5. Emit `SeriesIndicatorsUpdated(series, openTime, mode)`: mode LIVE only when
   `lastCompleteOpenTime == lastOpenTime` and the bar is fresh (close time within ~1 tf of now);
   otherwise BACKFILL (no strategy trigger). Coordinator itself is out of scope (listener stub).

## 5. Scheduling, ordering, concurrency
- Kafka consumer group, key = series -> horizontal scale by running more analysis instances.
- Per instance: bounded executor (~cores) fed from a **priority queue**: deadline = bar close + tf duration
  (M1 before H1 inside a burst); BACKFILL jobs lowest. Open-position boost later.
- Per-series guard: map `series -> running`; a trigger during a run sets `dirty`, one rerun after finish
  (triggers coalesced). Rebalance overlap covered by idempotent upserts.

## 6. Seeding: why ta4j must be customized, and the spike

### The problem
Each job is stateless: it starts with no ta4j objects in memory. Plain ta4j can only produce a value by
recomputing from candles:
- A recursive indicator (EMA, Wilder/MMA, RSI, ATR, ADX, MACD) computes `value(i)` from `value(i-1)`, which
  computes `value(i-2)`, and so on back to the first bar of the series. Its value is only trustworthy after
  a long warm-up (EMA(200) ~600-1,000 bars, ADX(14) ~150).
- So without customization every job must read ~1,000 candles and recompute the whole chain (~30 ms per
  series for all presets), even when only one new candle arrived. Worst burst (3,000 series): ~3M candle
  rows read and ~90 s CPU.
- The values computed by the previous job are already stored (`indicator_value`), but ta4j has no API to
  say "the EMA at bar 999 was X, continue from there".

### The idea: seeded building blocks
- ta4j's recursion goes through `getValue(i-1)` on the same indicator object (via its cache). If an indicator
  returns a **stored value for every index <= seedIndex**, the recursion stops at the seed and only the new
  bars are computed. EMA(200) then needs: the seed value + the new candles. No warm-up.
- Window indicators (SMA, std dev, highest/lowest, Stochastic, Bollinger) have no recursion: they only need
  their last N candles. They need no seed; the job reads `max window` candles before the range.
- Composite ta4j indicators create their internal parts privately (MACD's two EMAs, RSI's smoothed gain/loss
  averages, ADX's Wilder sums and DX smoothing). Those hidden parts are recursive too, so seeding only the
  final output is not enough: the recursion would restart inside the hidden parts.
- Therefore we do **not** use ta4j's composite classes directly for recursive indicators. We assemble them
  from our own small **seedable blocks**, so every recursive part is an explicit graph node whose value is
  stored and can be seeded.

### How (sketch)
- `SeedableIndicator` wrapper: holds the underlying ta4j-style computation, `seedIndex` and the stored
  value(s) for indexes <= seedIndex; `getValue(i)` returns the seed for `i <= seedIndex`, otherwise computes
  normally (and caches). Implemented as small subclasses of ta4j's `RecursiveCachedIndicator` /
  `CachedIndicator` (e.g. `SeededEMA` reusing ta4j's EMA formula).
- Blocks v1: EMA, Wilder/MMA, gain, loss, true range (needs previous close only), SMA (window, no seed).
- Composites rebuilt from blocks: RSI = 100 - 100/(1 + MMA(gain)/MMA(loss)); ATR = MMA(TR);
  MACD = EMA(fast) - EMA(slow), signal = EMA(MACD); ADX from +DM/-DM/TR Wilder sums.
- The ta4j series of a job holds only `max window + new bars` candles. Index of the first loaded candle is
  mapped to the seed index (the bar of the stored row).
- What is stored per bar: every node's value, outputs and hidden ones, in layout order (section 3).

### The spike (go/no-go)
1. Generate (or load) 1,000+ consecutive M1 candles.
2. Reference: plain ta4j full recompute of EMA(20/200), RSI(14), ATR(14), MACD(12,26,9) over all bars.
3. Seeded: compute bars 0..899 with our blocks, store every node value at bar 899, throw everything away;
   new job loads only `max window` + bars 900..999 and the stored row, seeds, computes 900..999.
4. Compare seeded vs reference for bars 900..999: must match within 1e-9.
5. Time the seeded "+1 bar" job (all presets) versus the 30 ms full recompute.
6. Outcome: if values match and cost drops, go with seeded blocks. If not, fall back to "read ~1,000 candles
   and recompute" (still affordable, see benchmark) and keep stored values as outputs only.

## 7. Indicator model: Spec, Definition, Catalog, Preset

### IndicatorSpec - "which indicator with which parameters"
- Value object: `type` (e.g. `EMA`) + `params` (map name -> value).
- Canonical form: defaults filled in, params sorted, numbers normalized -> string such as
  `EMA(period=20,source=CLOSE)` or `MACD(fast=12,signal=9,slow=26,source=CLOSE)`.
- Equality and hashing use the canonical form, so the same indicator requested twice (by two presets, or
  later by two strategies) is one node in the graph.
- Used as: graph node identity, key in the layout (column order of stored values), and later in strategy
  requirements.

### IndicatorDefinition - "how one indicator type works" (one class per type)
- `type()`: name, e.g. `RSI`.
- `paramSchema()`: parameter names, types, defaults, allowed ranges (e.g. period 2..500); validates and
  canonicalizes a spec.
- `children(spec)`: the specs it is built from - the dependency graph (RSI -> MMA(gain,14), MMA(loss,14);
  MACD -> EMA(12), EMA(26), and its signal EMA). Hidden children are listed here too.
- `window(spec)`: candles needed before the first new bar (0 for purely recursive, N for windows).
- `seedable()`: whether its own value is stored and used as a seed (recursive blocks) or recomputed
  from the window.
- `outputs()`: named outputs (MACD -> line, signal, histogram; Bollinger -> upper, middle, lower).
- `warmup(spec)`: bars needed for a correct value when there is **no** seed (first computation of a series
  or after a layout change); chained along children.
- `build(context, spec, children)`: creates the ta4j indicator(s) for this job, seeded where applicable.

### IndicatorCatalog - "all known types"
- Registry `type -> IndicatorDefinition`, filled at startup (Spring beans or a static list).
- Resolves a spec into a node graph: validate params, resolve children recursively, deduplicate by canonical
  spec, detect cycles, compute per series `max window`, `max warmup` and the layout (ordered node list +
  `layout_version` hash).
- Single place to exclude slow or unsupported types (RWIHigh, ta4j ParabolicSar in v1).

### Preset - "the well-known parameter sets we compute everywhere in v1"
- Named, ready specs: `EMA_20 = EMA(period=20)`, `RSI_14`, `MACD_12_26_9`, `BB_20_2`, `ATR_14`,
  `STOCH_14_3_3`, `ADX_14`, `SMA/EMA 9/20/50/200`, `VOLUME_SMA_20`.
- Config decides which presets apply to which timeframes (e.g. EMA(200) only from M15 up), so not every
  preset runs on every series.
- v1: the preset set *is* the indicator set of each series. Later, strategy subscriptions add their own
  specs on top; presets stay as defaults and as the commonly shared nodes (most strategies use them).
- Changing the preset set changes `layout_version`; affected series are recomputed from candles.

## Implementation steps
1. ~~**Seeding spike**~~ - done, go (see section 8: seeded == full recompute).
2. market-data event fields (section 1) - read tracking publisher/book first, keep change minimal.
3. analysis module skeleton: Kafka consumer, candle reader (JdbcClient), Flyway table.
4. ~~Engine: spec, definitions, graph, seedable blocks, presets~~ - done (section 8), 68 types, 74 presets;
   plus 33 self-built types (section 8d) and a fast RWI: 102 types, 108 presets.
5. Job + scheduler + per-series guard + update event.
6. Retention cleanup; metrics (job duration, queue wait, burst drain time).

## 8. Implementation status (2026-11-21): indicator engine
Package `com.rmrx.marketnexus.marketanalysis.technical.indicator` (module market-analysis), ta4j 0.18, `DoubleNum`.

### Structure
- `spec`: `IndicatorSpec` (canonical string = identity), `ParamValue` (int, decimal, enum, source), `Source`
  = `PriceField` (OPEN, HIGH, LOW, CLOSE, VOLUME, HL2, HLC3, OHLC4) or `OutputRef` (indicator on indicator,
  e.g. DEMA's second EMA has the first EMA as source).
- `schema`: `ParamDef` / `ParamSchema` (defaults, ranges, coercion).
- `definition`: `IndicatorDefinition` (type, family, models, params, outputs PUBLIC/INTERNAL, dependencies,
  lookback, warmup, build), `DefinitionBuilder` (fluent definition without a class), `definition.type`: one class
  for the first 9 types, then one class per family (`MovingAverageIndicators`, `TrendIndicators`, ...).
- `block`: `SeededIndicator` (generic seeding, see below), `LambdaBlock` (formula as lambda with access to its
  own previous values), `DelegateBlock` (wraps a pure-window ta4j indicator), `ExponentialBlock` (EMA/Wilder),
  `SmaBlock`, `StdDevBlock`, `WindowExtremeBlock`, `TrueRangeBlock`, `ChangeBlock`, `DirectionalMoveBlock`,
  `ObvBlock`, `FormulaBlock`, `PriceIndicator`.
- `catalog` (`IndicatorCatalog`, `DefaultIndicatorCatalog`), `graph` (`IndicatorGraph`, `IndicatorLayout`),
  `series` (`BarSeriesFactory`), `compute` (`IndicatorComputation`, `IndicatorRow`, `ComputationResult`),
  `preset` (`IndicatorPresets`), `IndicatorDemo` (manual check, see Verification).

### Seeding, as built
- Node = one output = one stored double per bar. Hidden recursive state is an INTERNAL output (RSI avgGain/avgLoss,
  ADX smoothed DMs, Stoch fastK, Supertrend bands, Mass Index EMAs, Fisher intermediate, Kalman covariance).
- `SeededIndicator.calculate` is final: stored value for bars up to the seed, else `compute`. ta4j recursion
  stops at the seed for every type. Cache filling starts after the seed (ta4j's `RecursiveCachedIndicator`
  pre-fills from bar 0 on big jumps, which breaks seeded jobs; found by the demo, fixed).
- Each definition declares `lookback` (past bars of any input it reads). `historyBars = max(1, max lookback)` is
  what a seeded job needs before the first new bar: candles and stored rows alike (199 with SMA 200).
- Rule for ported ta4j formulas with start-of-series branches (`index < n`): lookback must be at least that
  threshold, so the branch never runs in a seeded job (ZLEMA, KAMA, LSMA, MFI, Squeeze).
- Recursive indicators are ported formulas on our blocks; pure-window ta4j indicators (no recursion inside) are
  wrapped with `DelegateBlock`. Recursive ta4j classes are never used directly.

### Implemented (68 types)
| Family | Types |
|---|---|
| Price transform | TR, HIGHEST, LOWEST (TP / HL2 / OHLC4 are price sources) |
| Moving average | SMA, EMA, RMA (Wilder/SMMA/MMA), WMA, HMA, DEMA, TEMA, ZLEMA, KAMA, VIDYA, MCGINLEY, VWMA, LSMA |
| Trend | MACD, PPO, ADX (+DI/-DI), AROON (up/down/osc), SUPERTREND, ICHIMOKU (tenkan/kijun/spanA/spanB), DPO, MASS_INDEX, LINREG (value, slope), RWI (high/low, added 2026-11-23) |
| Momentum | RSI, STOCH, STOCH_RSI (+k/d), WILLIAMS_R, CCI, ROC, CMO, AO (+accelerator), KST (+signal), COPPOCK, FISHER (+trigger), SQUEEZE (value, level) |
| Volatility | ATR, BBANDS (+percentB, bandwidth), STDDEV, KELTNER, DONCHIAN, ULCER, CHANDELIER, CHOP |
| Volume | OBV, AD, CMF, CHAIKIN_OSC, MFI, VWAP (rolling), NVI, PVI (+signal), PVO; volume MA = SMA(source=VOLUME) |
| Statistical | VARIANCE, ZSCORE, KALMAN |
| Candlestick (1/0) | DOJI, BULLISH/BEARISH_ENGULFING, BULLISH/BEARISH_HARAMI, THREE_WHITE_SOLDIERS, THREE_BLACK_CROWS, HAMMER, HANGING_MAN, INVERTED_HAMMER, SHOOTING_STAR (hammer family uses a shared ADX(5) dependency as trend filter, like ta4j) |

Presets: every type with conventional defaults (74 presets, 88 graph nodes, 137 stored values per bar);
SMA/EMA 200 only from M15 up. With RWI and the self-built types of section 8d: 108 presets, 147 nodes, 230 stored
values per bar (history bars still 199, max warm-up 1,020).

RWI matches ta4j's RWIHigh/RWILow exactly. ta4j creates a new ATRIndicator per distance n on every call (each
recomputed from the series start), which made it pathologically slow; here ATR(2..period) are shared, seeded ATR
nodes, so a bar costs O(period) (12 extra stored ATR values per bar for RWI_14; ATR(14) is shared with ATR_14).

### Deliberate deviations from ta4j
- Bar without range (high == low, e.g. Nobitex filler candles): close location value = 0. ta4j gives NaN, which
  makes AD and CHAIKIN_OSC NaN for ever after and CMF NaN for its window.
- Flat windows do not poison recursive indicators: KAMA (efficiency ratio 0), VIDYA (CMO 0), FISHER (middle of
  range). ta4j: NaN for ever after. STOCH flat window = 50, STOCH_RSI flat = 0.5 (ta4j NaN).
- Extra outputs without ta4j counterpart: PPO/PVO signal + histogram, KST signal, STOCH_RSI k/d, FISHER trigger,
  NVI/PVI signal, SQUEEZE level.
- VIDYA has only `cmoPeriod` (ta4j's vidya period parameter is unused in its formula).
- OBV, AD, NVI, PVI are cumulative: anchored at the first bar computed without a seed; a recompute after a
  layout change moves the anchor.

### Verification results (`IndicatorDemo`, 5 sets x 1,000 M1 candles, all 74 presets)
- Full compute vs ta4j: 97 outputs, max relative diff ~1e-12, 0 mismatches; only the NaN deviations above.
- Seeded with first 500 rows vs full: max relative diff ~2e-10 (ta4j's SMA uses a running total whose rounding
  depends on where computation starts; affects wrapped window indicators only, not seeding).
- Each preset alone, seeded with exactly its own `historyBars`: 74/74 match (checks every declared lookback).
- Timing (warm JVM): full 1,000 bars ~25 ms, seeded +1 bar ~0.34 ms, seeded 500 bars ~21-30 ms.
  First run on a cold JVM ~0.4 s.

## 8b. Implementation status (2026-11-21): trigger -> job (market-analysis)
- Event copy in analysis carries the section 1 fields (market-data publisher not changed yet).
- `job.SeriesTrigger`: merged messages (newest complete point, oldest previous point, newest seen candle);
  LIVE = newest complete bar is the newest bar and not past its deadline (close + 1 tf).
- `job.schedule.SeriesJobScheduler`: fixed pool + priority queue (`JobPriority`: LIVE first, earliest deadline,
  smaller timeframe, arrival). Per series at most one running and one queued job; triggers during a run merge into one
  rerun. State table in the class javadoc.
- `job.compute.JobInputLoader`: start point from stored rows, not from the trigger. SEEDED = newest `historyBars`
  contiguous rows + candles up to `lastCompleteOpenTime` (cap `max-new-bars`); FULL = newest
  `min(completeLength, maxWarmup + initial-bars)` candles, first `maxWarmup` rows dropped. Decision: no compute while
  `completeLength < maxWarmup`.
- `store`: `CandleReader` (market-data `public.candle`, closed only), `IndicatorRowReader`
  (`analysis.indicator_value`, column `node_values`; Flyway in schema `analysis`).
- `analysis.indicator_layout` (V2): `entries[i]` names `node_values[i]`; saved once per timeframe graph.
- Storing: `IndicatorRowWriter` upserts a job's rows in one transaction (batched). A job capped by `max-new-bars`
  is queued again and continues from the rows it stored.
- `job.publish.SeriesIndicatorsUpdated` (topic `series-indicators-updated`, key = series): first/last stored bar,
  layout version, mode. LIVE only when the newest stored bar is the trigger's complete point, the series has no newer
  bar after a gap, the job was not capped, and the bar is still within its deadline when stored; else BACKFILL.
- Not yet: market-data publisher fields, strategy consumer, retention, startup sweep, metrics.

## 8c. Storage layout and ordering (decision, 2026-11-22)
- One row per bar per series and layout: `indicator_value.node_values float8[]` holds every node output of the series
  (135 on M1: more than the 74 presets because hidden dependencies are nodes too, many indicators have several outputs,
  and recursive state is stored as INTERNAL outputs, e.g. RSI avgGain/avgLoss, ADX smoothed moves, Supertrend bands).
  Why an array: a job always reads/writes all nodes of a bar together; row per output would be ~135x rows and upserts.
- **One list drives everything**: `IndicatorGraph.layout()` (nodes in dependency order, outputs in definition order).
  The stored names (`indicator_layout.entries`), the written values (`IndicatorComputation` builds its columns by
  iterating the layout) and the seeds read back (column i -> layout entry i) all use it. The order in which ta4j
  computes nodes does not matter.
- `layout_version` = hash of the stored names. `indicator_layout.internal[i]` marks hidden seed state.
- Seeding INTERNAL state: definitions build their recursive blocks with `context.seed(spec, output)`; every block
  extends `SeededIndicator`, whose `calculate` returns the stored value up to the seed bar, so recursion stops there.
- **Layout change** (indicator added/removed/changed): no seed for the new version -> FULL recompute of all indicators
  (newest `maxWarmup + initial-bars` candles) into NEW rows. PK includes `layout_version`, so the old layout's rows stay
  (removed only by retention later).
- **Formula change** of an existing type: bump `IndicatorDefinition.version()`; its stored name becomes
  `TYPE@version(...)` -> new layout -> new rows; old values stay under the old layout. (Nested source specs keep their
  plain name, but the layout hash still changes because the changed node itself is in the layout.)
- `indicator_definition` (type, version, family, params, outputs): descriptive copy of the catalog, one row per formula
  version, written on startup together with the layouts of all timeframes.
- Reading: `service.IndicatorService.fetchIndicators(series, indicator | null, limit)` -> newest bars first,
  `IndicatorSnapshot(openTime, layoutVersion, name -> value)`. Each row is mapped through its own layout; a bar stored
  under several layouts is taken from the newest one; one indicator = its public outputs, and only layouts containing
  it; all = public outputs only.

## 8d. Implementation status (2026-11-23): self-built types without a ta4j counterpart
Package `...technical.indicator.definition.custom` keeps them apart from the ta4j-based types in `definition.type`:
one class per family (`CustomMovingAverageIndicators`, `CustomTrendIndicators`, `CustomMomentumIndicators`,
`CustomVolatilityIndicators`, `CustomVolumeIndicators`, `CustomStatisticalIndicators`,
`CustomPriceTransformIndicators`), collected by `CustomIndicators.definitions()`; `DefaultIndicatorCatalog` adds them
with one `addAll`. Presets in `preset.CustomIndicatorPresets` (each type with conventional defaults, all timeframes),
appended to `IndicatorPresets.all()`. Same building blocks and seeding rules as section 8 (`DefinitionSupport` is
public for this).

### Reference policy
- ta4j stays the implementation reference and conventions source (EMA started with the first input, same blocks).
- Types ta4j lacks are built by us; each javadoc names the definition it follows (TradingView, TA-Lib, pandas-ta,
  original author).
- TA-Lib (Java, `com.tictactec:ta-lib`, test scope) and pandas-ta are used **only to compare** the custom types, never
  at runtime. The original pandas-ta does not install on Python 3.14; the maintained fork `pandas_ta_classic` (same
  API) is pinned in `tools/indicator-reference/requirements.txt`.

### Implemented (33 types)
| Family | Types | Reference |
|---|---|---|
| Price transform | HEIKIN_ASHI (open/high/low/close) | pandas-ta |
| Moving average | T3, ALMA, FRAMA, ALLIGATOR (jaw/teeth/lips) | T3: TA-Lib + pandas-ta; ALMA: pandas-ta; FRAMA: formula (Ehlers); ALLIGATOR: formula |
| Trend | ELDER_RAY (bull/bear), TRIX (+signal), STC, VORTEX (plus/minus), PSAR | pandas-ta; TRIX also TA-Lib; PSAR: TA-Lib |
| Momentum | BOP (+smoothed), TSI (+signal), CONNORS_RSI, ULTOSC, RVGI (+signal), SQUEEZE_MOMENTUM | BOP/ULTOSC: TA-Lib + pandas-ta; CONNORS_RSI: formula (TradingView) |
| Volatility | NATR, MA_ENVELOPE (upper/middle/lower), CHAIKIN_VOL, HV | NATR: TA-Lib + pandas-ta; others formula |
| Volume | RVOL, FORCE_INDEX, PVT, KLINGER (+signal), EOM | pandas-ta; RVOL formula |
| Statistical | LOG_RETURN, PERCENT_RANK, QUANTILE (preset MEDIAN_20), R_SQUARED, AUTOCORR, HURST, ENTROPY, FRACTAL_DIM | LOG_RETURN/MEDIAN/ENTROPY/AUTOCORR: pandas; others formula |

### Formula choices worth knowing
- KLINGER is the simplified signed-volume form (pandas-ta `kvo`), not Klinger's trend/cumulative-measurement form.
- PVT follows TradingView (fraction change * volume); pandas-ta's is 100x (its roc is in percent).
- ALMA follows TradingView (Gaussian peak near the newest bar for offset 0.85); pandas_ta_classic applies the weights
  reversed, which equals offset 1 - x.
- ENTROPY is Shannon entropy of one window (pandas_ta_classic); the original pandas-ta used nested windows.
- TRIX signal is an EMA (pandas-ta: SMA, so not compared). EOM divisor 10,000 (TradingView; pandas-ta default 1e8).
- HV annualizes with 365 x 24 h and the bar duration (crypto). HURST: R/S over chunk sizes n, n/2, ... >= 8, 0.5 when
  fewer than two sizes are usable. CONNORS_RSI percent rank and PERCENT_RANK count previous values <= current
  (TradingView). FRAMA uses alpha 0.01 on a flat window.
- Flat filler candles give 0 / neutral values, never NaN that would poison recursion (as in section 8).
- ALLIGATOR: jaw/teeth/lips = shared RMA (SMMA) nodes of HL2 (13/8/5) read 8/5/3 bars back. The chart's forward
  shift is a lag at bar i (value of bar i - shift), so it needs only lookback, no future data.
- PSAR is Wilder's algorithm as in TA-Lib `SAR` (start direction from the minus DM of the first two bars), not ta4j's
  variant (ta4j's class is pathologically slow). Its state after each bar (next SAR, EP, AF, trend) is stored, so a
  seeded job does O(1) work per bar.
- Recursive state stored as INTERNAL outputs: PSAR (nextSar, ep, af, trend), TSI (4 EMAs), KLINGER (2 EMAs), STC (stoch1, smooth1, stoch2),
  CONNORS_RSI (streak, streak avg gain/loss), CHAIKIN_VOL (range EMA). T3 and TRIX use shared nested EMA nodes.

### Verification (2026-11-23)
- `IndicatorDemo`: full vs ta4j unchanged (custom types skipped there); seeded vs full max ~2e-10; each preset alone
  seeded with its own history bars: 108/108 (with RWI, PSAR and ALLIGATOR). Timing (warm): full 1,000 bars ~55-90 ms, seeded 500 bars ~30-40 ms
  (before the custom types: ~25 ms / ~21-30 ms).
- `ReferenceComparison` (market-analysis test sources, manual `main`, custom presets only): bars 500..999 of 5 sets,
  diff normalized by the largest reference value. TA-Lib 6/6 (PSAR exact) and pandas-ta 29/29 outputs within 1e-6 (most ~1e-13;
  KLINGER ~2e-7 and STC ~1e-8 from different smoothing starts). 14 outputs have no reference and are checked against
  their formula only (see table). RWI is checked against ta4j in `IndicatorDemo`.
- Rerun: `ReferenceComparison` once (writes candle CSVs to `market-analysis/target/indicator-reference`), then
  `tools/indicator-reference/.venv/Scripts/python tools/indicator-reference/pandas_ta_reference.py
  market-analysis/target/indicator-reference`, then `ReferenceComparison` again.

## 9. Not implemented (backlog)
Reasons: **model** = does not fit the append-only, per-series seeded model;
**not per bar** = output is not one value per bar or is strategy logic. (The former "no ta4j" rows are implemented as
self-built types, section 8d.)

| Indicator | Reason | Notes for later |
|---|---|---|
| Session VWAP, anchored VWAP | model (anchor/session reset) | ta4j VWAP is rolling only (implemented); needs a calendar/anchor rule parameter |
| Pivot points (classic, Fibonacci, Camarilla, Woodie, DeMark) | model (cross-timeframe) | ta4j has them; input is the previous higher-TF period |
| Average daily range | model (cross-timeframe) | needs D1 bars on intraday series |
| Higher-TF indicator on lower TF, intrabar inputs | model (cross-timeframe) | only last closed higher-TF value; needs the multi-TF barrier |
| Correlation, beta, relative strength, spread / pair z-score, market breadth | model (cross-symbol) | needs other symbols' bars for the same timestamp |
| Fractals, swing high/low, ZigZag, support/resistance, market structure (BOS/CHoCH), divergence, Fibonacci retracement, order blocks / FVG | model (repainting/structural) | ta4j has RecentSwingHigh/Low; need event outputs with confirmation delay |
| Ichimoku chikou | model (future value) | close shifted back: value of a bar known only 26 bars later |
| Linear regression intercept | model | ta4j uses the absolute bar index as x; differs between seeded job and full compute (value and slope are offered) |

### Deferred: not one value per bar (left out of the custom types on purpose)
| Indicator | Why it does not fit | What it would need |
|---|---|---|
| Volume profile, TPO / market profile | output is a histogram (volume or time per price level), not one number per bar | a separate output model (fixed bins per bar or a per-series profile table) and preferably lower-timeframe input |
| MA cross / trend state | strategy logic: an event or state derived from two indicators, not a market measure | belongs in the strategy layer, which reads the MAs it compares |
| MA ribbon | only a set of moving averages | strategies request the MA specs they need; nothing new to compute (MA_ENVELOPE is implemented) |
| Time-of-day RVOL | compares volume with the same time of day on previous days, needs same-hour history far beyond the job window | a calendar-aware lookup (e.g. per hour-of-day averages) outside the per-series window; plain RVOL (V / SMA(V)) is implemented |

## Open TODOs
- Unfillable gaps policy (market-data).
- Strategy coordinator (multi-timeframe barrier) consuming `SeriesIndicatorsUpdated`.
- Deferred "not per bar" indicators and the model-dependent rows (section 9).
- Preset set is large (230 stored values per bar, full compute ~55-90 ms); decide per timeframe which presets really
  run.

## Verification
- Spike: seeded values == full recompute (tolerance 1e-9) for each block/composite.
- Local stack: run market-data + analysis, watch rows appear per new M1/M5 candle; compare newest stored
  values with a one-off full ta4j recompute from DB candles.
- Fill a gap (reconciliation) -> bars after it computed once, BACKFILL events only.
- No unit tests unless asked.
