"""Reference values for the self-built indicator types (market-analysis, definition.custom).

Reads candles_setN.csv written by ReferenceComparison (market-analysis test sources) and writes pandas_setN.csv with
one column per compared output, named like the preset output ("ALMA_9_0_85_6.value"). ReferenceComparison then
compares them with our values.

Usage (from the repository root, after running ReferenceComparison once):
    python tools/indicator-reference/pandas_ta_reference.py market-analysis/target/indicator-reference

Needs pandas and pandas-ta. The original pandas-ta has no build for current Python versions; the maintained fork
pandas_ta_classic (same API) is used when present. See requirements.txt for the pinned versions.

Only parameters are mapped here; each formula variant is documented in the Java definition. Indicators whose pandas-ta
definition differs on purpose (e.g. TRIX signal = SMA in pandas-ta, EMA in ours) are not compared for that output.
"""
import glob
import os
import sys

import pandas as pd

try:
    import pandas_ta_classic as ta
except ImportError:  # original package, older Python versions
    import pandas_ta as ta


def first(frame):
    """First column of a DataFrame result, or the Series itself."""
    return frame.iloc[:, 0] if isinstance(frame, pd.DataFrame) else frame


def second(frame):
    return frame.iloc[:, 1]


def column(frame, index):
    return frame.iloc[:, index]


def autocorr(series, length, lag):
    """Plain pandas: autocorrelation inside each window (Series.autocorr)."""
    return series.rolling(length).apply(lambda w: pd.Series(w).autocorr(lag), raw=True)


def references(c):
    o, h, l, cl, v = c["open"], c["high"], c["low"], c["close"], c["volume"]
    # name -> function returning a Series; each is evaluated on its own so one failing call does not stop the rest
    return {
        "LOG_RETURN_1.value": lambda: ta.log_return(cl, length=1),
        "NATR_14.value": lambda: ta.natr(h, l, cl, length=14),
        "BOP_14.value": lambda: ta.bop(o, h, l, cl),
        "ELDER_RAY_13.bull": lambda: first(ta.eri(h, l, cl, length=13)),
        "ELDER_RAY_13.bear": lambda: second(ta.eri(h, l, cl, length=13)),
        "FORCE_INDEX_13.value": lambda: ta.efi(cl, v, length=13),
        # pandas-ta's roc is in percent, so its PVT is 100x the TradingView PVT that we follow
        "PVT.value": lambda: ta.pvt(cl, v) / 100,
        "HEIKIN_ASHI.open": lambda: column(ta.ha(o, h, l, cl), 0),
        "HEIKIN_ASHI.high": lambda: column(ta.ha(o, h, l, cl), 1),
        "HEIKIN_ASHI.low": lambda: column(ta.ha(o, h, l, cl), 2),
        "HEIKIN_ASHI.close": lambda: column(ta.ha(o, h, l, cl), 3),
        "T3_5_0_7.value": lambda: ta.t3(cl, length=5, a=0.7),
        "TRIX_15_9.value": lambda: first(ta.trix(cl, length=15, signal=9)),
        "TSI_25_13_13.value": lambda: first(ta.tsi(cl, fast=13, slow=25, signal=13)),
        "TSI_25_13_13.signal": lambda: second(ta.tsi(cl, fast=13, slow=25, signal=13)),
        "KLINGER_34_55_13.value": lambda: first(ta.kvo(h, l, cl, v, fast=34, slow=55, signal=13)),
        "KLINGER_34_55_13.signal": lambda: second(ta.kvo(h, l, cl, v, fast=34, slow=55, signal=13)),
        "STC_23_50_10.value": lambda: first(ta.stc(cl, tclength=10, fast=23, slow=50, factor=0.5)),
        # pandas_ta_classic applies the Gaussian weights reversed (peak near the oldest bar for offset 0.85);
        # reversed weights with offset 1 - x equal TradingView's weights with offset x, which we follow
        "ALMA_9_0_85_6.value": lambda: ta.alma(cl, length=9, sigma=6, distribution_offset=1 - 0.85),
        "VORTEX_14.plus": lambda: first(ta.vortex(h, l, cl, length=14)),
        "VORTEX_14.minus": lambda: second(ta.vortex(h, l, cl, length=14)),
        "ULTOSC_7_14_28.value": lambda: ta.uo(h, l, cl, fast=7, medium=14, slow=28),
        # columns: histogram, rvgi, signal
        "RVGI_10.value": lambda: column(ta.rvgi(o, h, l, cl, length=10, swma_length=4), 1),
        "RVGI_10.signal": lambda: column(ta.rvgi(o, h, l, cl, length=10, swma_length=4), 2),
        "EOM_14.value": lambda: ta.eom(h, l, cl, v, length=14, divisor=10000),
        "SQUEEZE_MOMENTUM_20.value": lambda: first(
            ta.squeeze(h, l, cl, bb_length=20, kc_length=20, lazybear=True)),
        "MEDIAN_20.value": lambda: ta.median(cl, length=20),
        "ENTROPY_10.value": lambda: ta.entropy(cl, length=10, base=2),
        "AUTOCORR_20_1.value": lambda: autocorr(cl, 20, 1),
    }


def main(directory):
    files = sorted(glob.glob(os.path.join(directory, "candles_set*.csv")))
    if not files:
        sys.exit(f"no candles_set*.csv in {directory}: run ReferenceComparison first")
    for path in files:
        candles = pd.read_csv(path)
        out = pd.DataFrame(index=candles.index)
        failed = []
        for name, compute in references(candles).items():
            try:
                series = compute()
                out[name] = pd.Series(series).reset_index(drop=True).astype(float)
            except Exception as e:  # report and continue with the other indicators
                failed.append(f"{name}: {type(e).__name__}: {e}")
        target = path.replace("candles_set", "pandas_set")
        out.to_csv(target, index=False, na_rep="", float_format="%.17g")
        print(f"{os.path.basename(target)}: {len(out.columns)} columns")
        for line in failed:
            print("  FAILED " + line)


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "market-analysis/target/indicator-reference")
