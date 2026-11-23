# analysis: indicator computation

Engine: module `market-analysis`, package `marketanalysis.technical.indicator`, ta4j 0.18 (`DoubleNum`).

## Model
- `IndicatorSpec` = type + params; canonical string (defaults filled, params sorted) is the identity → same spec is
  one graph node however often it is requested.
- `IndicatorDefinition` per type (one class per family, or `DefinitionBuilder`): param schema, outputs
  (PUBLIC / INTERNAL), dependencies, `lookback`, `warmup`, `build`. `version()` bump = formula change → new stored
  name `TYPE@v(...)` → new layout.
- `IndicatorCatalog` resolves specs into an `IndicatorGraph` (dedup, cycles, `maxWarmup`, `historyBars`, layout).
- v1 indicator set = **presets only** (system indicators, conventional defaults, SMA/EMA 200 only from M15 up).
  Strategies are built on top of these values later.

## Taxonomy (from the indicator catalog study)
- Families (`IndicatorFamily`): price transform, moving average, trend, momentum, volatility, volume, statistical,
  structure, volume profile, candlestick pattern, cross-market (last three/structure not implemented).
- Computation models: **W** window (exact after N bars), **R** recursive/IIR (converges, never exact), **C**
  cumulative/anchored (value depends on anchor), **X** composite (dependency chain), **P** repainting/structural
  (breaks append-only), **S** stateless per bar.
- Warm-up rules of thumb for R: EMA 3–5N, RMA/Wilder 5–10N, chained (DEMA/TEMA/T3/TRIX) multiply, ADX ~150 bars.
  Hence `maxWarmup` 1,020 for the preset set.
- Terms: warm-up = bars until a value is trustworthy; lookback = past bars a formula reads (seeded job needs
  `historyBars = max lookback`); instance key = (symbol, timeframe, type, params).

## Seeding (core decision)
- Jobs are stateless but **seeded**: every node output, including hidden recursive state (RSI avgGain/avgLoss, ADX
  smoothed DMs, PSAR state, EMA legs…) is stored per bar. `SeededIndicator.calculate` returns the stored value up to
  the seed bar, so ta4j recursion stops there. A job reads only `historyBars` (max lookback, 199) rows + candles.
- Why: plain ta4j recomputes recursive chains from bar 0 (~1,000 bars warm-up). Seeded +1 bar ≈ 0.34 ms vs full
  1,000 bars ≈ 55–90 ms. Verified: seeded == full within ~2e-10.
- Recursive ta4j classes are never used directly (they create hidden parts privately); recursive formulas are ported
  onto own blocks. Pure-window ta4j indicators are wrapped (`DelegateBlock`).
- Rule: start-of-series branches (`index < n`) must be covered by `lookback` so they never run in seeded jobs.

## What we have
- 102 types, 108 presets, ~147 nodes, 228–230 stored values per bar (194–196 public).
- ta4j-based (68): MAs (SMA…LSMA), trend (MACD, PPO, ADX, Aroon, Supertrend, Ichimoku, DPO, Mass, LinReg, RWI),
  momentum (RSI, Stoch, StochRSI, W%R, CCI, ROC, CMO, AO, KST, Coppock, Fisher, Squeeze), volatility (ATR, BB, StdDev,
  Keltner, Donchian, Ulcer, Chandelier, Chop), volume (OBV, AD, CMF, Chaikin, MFI, rolling VWAP, NVI/PVI, PVO),
  statistical, candlestick patterns.
- Self-built (33, package `definition.custom`, kept visibly apart): Heikin-Ashi, T3, ALMA, FRAMA, Alligator,
  Elder Ray, TRIX, STC, Vortex, PSAR (Wilder/TA-Lib, fast), BOP, TSI, Connors RSI, ULTOSC, RVGI, Squeeze Momentum,
  NATR, MA envelope, Chaikin vol, HV, RVOL, Force, PVT, Klinger, EOM, log return, percent rank, quantile, R², autocorr,
  Hurst, entropy, fractal dim.
- References: ta4j = implementation reference; TA-Lib / pandas_ta_classic only to compare custom types (test scope,
  `tools/indicator-reference`), never at runtime.
- Deliberate deviations: flat/zero inputs give 0/neutral, never NaN that poisons recursion (filler candles); PPO/PVO
  line = 0 when slow EMA = 0 (bug found in test run); cumulative OBV/AD/NVI/PVI anchored at first unseeded bar.
- RWIHigh/ParabolicSar of ta4j excluded (pathologically slow) → own RWI (shared seeded ATRs) and PSAR.

## Formula choices worth knowing (custom types)
- KLINGER = simplified signed-volume form (pandas-ta `kvo`); PVT = TradingView (fraction × volume, pandas-ta is 100x);
  ALMA = TradingView weights (pandas_ta_classic applies them reversed); ENTROPY = Shannon entropy of one window;
  TRIX signal = EMA; EOM divisor 10,000 (TradingView).
- HV annualized with 365 × 24 h and bar duration (crypto). HURST = R/S over chunk sizes n, n/2, … ≥ 8 (0.5 if fewer
  than two sizes). PERCENT_RANK / Connors percent rank count previous values ≤ current. FRAMA alpha 0.01 on flat window.
- ALLIGATOR: shared RMA(HL2) 13/8/5 read 8/5/3 bars back (chart shift = lag, no future data).
- Recursive state stored as INTERNAL outputs: PSAR (nextSar, ep, af, trend), TSI (4 EMAs), KLINGER, STC, Connors RSI,
  Chaikin vol; T3/TRIX via shared nested EMA nodes.

## Verification
- `IndicatorEngineTest` (JUnit, `mvn test -pl market-analysis`, 5 sets × 1,000 synthetic M1 candles, all presets):
  full vs ta4j (`Ta4jReference`, ~1e-12), seeded vs full (~2e-10, all candles and job candles), each preset alone
  seeded with its own `historyBars` (108 dynamic tests). Tolerance 1e-9; ta4j NaN where we give a number is allowed.
- `ReferenceComparison` (test sources, manual main, custom presets): TA-Lib 6/6 and pandas-ta 29/29 outputs within 1e-6.
  Rerun: run it once (writes CSVs to `market-analysis/target/indicator-reference`), then
  `tools/indicator-reference/.venv/Scripts/python tools/indicator-reference/pandas_ta_reference.py
  market-analysis/target/indicator-reference`, then run it again. (pandas_ta_classic pinned in
  `tools/indicator-reference/requirements.txt`; original pandas-ta does not install on Python 3.14.)

## Missing and why
- **Model (not append-only per-series)**: session/anchored VWAP (anchor reset); pivot points, ADR, higher-TF on lower
  TF (cross-timeframe); correlation, beta, relative strength, pair z-score, breadth (cross-symbol); fractals, swings,
  ZigZag, S/R, BOS/CHoCH, divergence, Fib, order blocks/FVG (repainting/structural, need confirmed events); Ichimoku
  chikou (future value); LinReg intercept (absolute bar index differs seeded vs full).
- **Not one value per bar** (deferred): volume profile / TPO (histogram output, needs own model), time-of-day RVOL
  (needs same-hour history far beyond the job window).
- **Strategy logic, not indicators** (by decision): MA cross / trend state, MA ribbon signals → strategy layer reads
  the MAs it needs.
- **Non-OHLCV data**: CVD, volume delta, footprint, spread, order-book depth/imbalance, funding/OI need trade or
  order-book streams (not ingested).
