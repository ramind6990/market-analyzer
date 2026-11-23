package com.rmrx.marketanalyzer.marketanalysis.technical.indicator.definition.custom;

import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.definition.IndicatorDefinition;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.definition.OutputDef;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.schema.ParamDef;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.spec.PriceField;
import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;

import java.util.Arrays;
import java.util.List;

import static com.rmrx.marketanalyzer.marketanalysis.technical.indicator.definition.ComputationModel.WINDOW;
import static com.rmrx.marketanalyzer.marketanalysis.technical.indicator.definition.DefinitionBuilder.define;
import static com.rmrx.marketanalyzer.marketanalysis.technical.indicator.definition.IndicatorFamily.STATISTICAL;
import static com.rmrx.marketanalyzer.marketanalysis.technical.indicator.definition.type.DefinitionSupport.*;

/** Statistical indicators without a ta4j indicator. */
public final class CustomStatisticalIndicators {

    public static final String LOG_RETURN = "LOG_RETURN";
    public static final String PERCENT_RANK = "PERCENT_RANK";
    public static final String QUANTILE = "QUANTILE";
    public static final String R_SQUARED = "R_SQUARED";
    public static final String AUTOCORR = "AUTOCORR";
    public static final String HURST = "HURST";
    public static final String ENTROPY = "ENTROPY";
    public static final String FRACTAL_DIM = "FRACTAL_DIM";

    private CustomStatisticalIndicators() {
    }

    public static List<IndicatorDefinition> definitions() {
        return List.of(logReturn(), percentRank(), quantile(), rSquared(), autocorrelation(), hurst(), entropy(),
                fractalDimension());
    }

    /** Percent rank (TradingView {@code ta.percentrank}): % of the previous n values less than or equal to the current. */
    static IndicatorDefinition percentRank() {
        return define(PERCENT_RANK).description("Percent rank").family(STATISTICAL).models(WINDOW)
                .params(period(100), source(PriceField.CLOSE)).outputs(OutputDef.value("percent rank 0..100"))
                .lookback(s -> period(s)).warmup(s -> period(s) + 1)
                .build((c, s) -> {
                    Indicator<Num> source = source(c, s);
                    int begin = c.series().getBeginIndex();
                    int n = period(s);
                    return single(lambda(c, s, VALUE, (i, self) -> {
                        if (i - n < begin) {
                            return Double.NaN;
                        }
                        double current = v(source, i);
                        int count = 0;
                        for (int j = i - n; j < i; j++) {
                            if (v(source, j) <= current) {
                                count++;
                            }
                        }
                        return 100.0 * count / n;
                    }));
                });
    }

    /** Rolling quantile with linear interpolation (pandas {@code rolling().quantile}, default); median = q 0.5. */
    static IndicatorDefinition quantile() {
        return define(QUANTILE).description("Rolling quantile").family(STATISTICAL).models(WINDOW)
                .params(period(20), ParamDef.decimal("q", 0.5, 0, 1, "quantile, 0.5 = median"), source(PriceField.CLOSE))
                .outputs(OutputDef.value("quantile"))
                .lookback(s -> period(s) - 1).warmup(s -> period(s))
                .build((c, s) -> {
                    Indicator<Num> source = source(c, s);
                    int begin = c.series().getBeginIndex();
                    int n = period(s);
                    double q = decimal(s, "q");
                    return single(lambda(c, s, VALUE, (i, self) -> {
                        double[] window = window(source, i, n, begin);
                        if (window == null) {
                            return Double.NaN;
                        }
                        Arrays.sort(window);
                        double position = q * (n - 1);
                        int lower = (int) Math.floor(position);
                        int upper = Math.min(n - 1, lower + 1);
                        return window[lower] + (position - lower) * (window[upper] - window[lower]);
                    }));
                });
    }

    /** Coefficient of determination of the least-squares line through the last n values (x = 0..n-1); 0 if flat. */
    static IndicatorDefinition rSquared() {
        return define(R_SQUARED).description("R-squared of the linear trend").family(STATISTICAL).models(WINDOW)
                .params(ParamDef.integer(PERIOD, 20, 2, 1000, "bars"), source(PriceField.CLOSE))
                .outputs(OutputDef.value("R^2 0..1"))
                .lookback(s -> period(s) - 1).warmup(s -> period(s))
                .build((c, s) -> {
                    Indicator<Num> source = source(c, s);
                    int begin = c.series().getBeginIndex();
                    int n = period(s);
                    double[] x = new double[n];
                    for (int k = 0; k < n; k++) {
                        x[k] = k;
                    }
                    return single(lambda(c, s, VALUE, (i, self) -> {
                        double[] y = window(source, i, n, begin);
                        if (y == null) {
                            return Double.NaN;
                        }
                        double r = correlation(x, 0, y, 0, n);
                        return r * r;
                    }));
                });
    }

    /**
     * Autocorrelation at {@code lag} inside the last n values (pandas {@code rolling(n).apply(s.autocorr(lag))}):
     * Pearson correlation of the n - lag pairs (y[k], y[k - lag]). 0 when one side is flat.
     */
    static IndicatorDefinition autocorrelation() {
        return define(AUTOCORR).description("Rolling autocorrelation").family(STATISTICAL).models(WINDOW)
                .params(period(20), ParamDef.period("lag", 1, "bars between the paired values"),
                        source(PriceField.CLOSE))
                .outputs(OutputDef.value("autocorrelation -1..1"))
                .validate(s -> param(s, "lag") <= period(s) - 2, "lag must leave at least 2 pairs in the window")
                .lookback(s -> period(s) - 1).warmup(s -> period(s))
                .build((c, s) -> {
                    Indicator<Num> source = source(c, s);
                    int begin = c.series().getBeginIndex();
                    int n = period(s);
                    int lag = param(s, "lag");
                    return single(lambda(c, s, VALUE, (i, self) -> {
                        double[] y = window(source, i, n, begin);
                        return y == null ? Double.NaN : correlation(y, lag, y, 0, n - lag);
                    }));
                });
    }

    /**
     * Hurst exponent by rescaled range (R/S) of the last n log returns: chunk sizes n, n/2, n/4, ... (at least 8);
     * per size the mean R/S of its chunks (R = range of cumulative deviations from the chunk mean, S = population
     * standard deviation, chunks with S = 0 skipped); value = least-squares slope of ln(R/S) over ln(size).
     * 0.5 (random walk) when fewer than two sizes give a value.
     */
    static IndicatorDefinition hurst() {
        return define(HURST).description("Hurst exponent (R/S)").family(STATISTICAL).models(WINDOW)
                .params(ParamDef.integer(PERIOD, 100, 32, 1000, "log returns"), source(PriceField.CLOSE))
                .outputs(OutputDef.value("Hurst exponent"))
                .lookback(s -> period(s)).warmup(s -> period(s) + 1)
                .build((c, s) -> {
                    Indicator<Num> source = source(c, s);
                    int begin = c.series().getBeginIndex();
                    int n = period(s);
                    return single(lambda(c, s, VALUE, (i, self) -> {
                        if (i - n < begin) {
                            return Double.NaN;
                        }
                        double[] returns = new double[n];
                        for (int k = 0; k < n; k++) {
                            double now = v(source, i - n + 1 + k);
                            double before = v(source, i - n + k);
                            returns[k] = now > 0 && before > 0 ? Math.log(now / before) : 0;
                        }
                        return hurstExponent(returns);
                    }));
                });
    }

    private static double hurstExponent(double[] returns) {
        int count = 0;
        double[] logSize = new double[16];
        double[] logRs = new double[16];
        for (int size = returns.length; size >= 8; size /= 2) {
            double rsSum = 0;
            int chunks = 0;
            for (int start = 0; start + size <= returns.length; start += size) {
                double rs = rescaledRange(returns, start, size);
                if (!Double.isNaN(rs)) {
                    rsSum += rs;
                    chunks++;
                }
            }
            if (chunks > 0 && rsSum > 0) {
                logSize[count] = Math.log(size);
                logRs[count] = Math.log(rsSum / chunks);
                count++;
            }
        }
        if (count < 2) {
            return 0.5;
        }
        double meanX = 0;
        double meanY = 0;
        for (int k = 0; k < count; k++) {
            meanX += logSize[k] / count;
            meanY += logRs[k] / count;
        }
        double covariance = 0;
        double varianceX = 0;
        for (int k = 0; k < count; k++) {
            covariance += (logSize[k] - meanX) * (logRs[k] - meanY);
            varianceX += (logSize[k] - meanX) * (logSize[k] - meanX);
        }
        return covariance / varianceX;
    }

    /** R/S of one chunk; NaN when its standard deviation is 0. */
    private static double rescaledRange(double[] values, int start, int size) {
        double mean = 0;
        for (int k = start; k < start + size; k++) {
            mean += values[k] / size;
        }
        double cumulative = 0;
        double max = 0;
        double min = 0;
        double squares = 0;
        for (int k = start; k < start + size; k++) {
            double deviation = values[k] - mean;
            cumulative += deviation;
            max = Math.max(max, cumulative);
            min = Math.min(min, cumulative);
            squares += deviation * deviation;
        }
        double sd = Math.sqrt(squares / size);
        return sd == 0 ? Double.NaN : (max - min) / sd;
    }

    /**
     * Shannon entropy of the window (pandas_ta_classic {@code entropy}): p = x / sum of the last n values,
     * value = sum over the window of -p * log_base(p). Non-positive p contribute 0; NaN when the window sums to 0.
     * (The original pandas-ta took each p over its own trailing window and summed over a second window.)
     */
    static IndicatorDefinition entropy() {
        return define(ENTROPY).description("Shannon entropy of the window").family(STATISTICAL).models(WINDOW)
                .params(period(10), ParamDef.decimal("base", 2, 1.01, 100, "logarithm base"), source(PriceField.CLOSE))
                .outputs(OutputDef.value("entropy"))
                .lookback(s -> period(s) - 1).warmup(s -> period(s))
                .build((c, s) -> {
                    Indicator<Num> source = source(c, s);
                    int begin = c.series().getBeginIndex();
                    int n = period(s);
                    double logBase = Math.log(decimal(s, "base"));
                    return single(lambda(c, s, VALUE, (i, self) -> {
                        double[] window = window(source, i, n, begin);
                        if (window == null) {
                            return Double.NaN;
                        }
                        double sum = Arrays.stream(window).sum();
                        if (sum == 0) {
                            return Double.NaN;
                        }
                        double total = 0;
                        for (double x : window) {
                            double p = x / sum;
                            if (p > 0) {
                                total -= p * Math.log(p) / logBase;
                            }
                        }
                        return total;
                    }));
                });
    }

    /**
     * Sevcik fractal dimension of the last n values: values and time scaled to 0..1, L = length of the polyline,
     * D = 1 + ln(L) / ln(2 (n - 1)). A flat window gives 1.
     */
    static IndicatorDefinition fractalDimension() {
        return define(FRACTAL_DIM).description("Fractal dimension (Sevcik)").family(STATISTICAL).models(WINDOW)
                .params(ParamDef.integer(PERIOD, 30, 3, 1000, "bars"), source(PriceField.CLOSE))
                .outputs(OutputDef.value("fractal dimension 1..2"))
                .lookback(s -> period(s) - 1).warmup(s -> period(s))
                .build((c, s) -> {
                    Indicator<Num> source = source(c, s);
                    int begin = c.series().getBeginIndex();
                    int n = period(s);
                    return single(lambda(c, s, VALUE, (i, self) -> {
                        double[] y = window(source, i, n, begin);
                        if (y == null) {
                            return Double.NaN;
                        }
                        double max = Arrays.stream(y).max().orElseThrow();
                        double min = Arrays.stream(y).min().orElseThrow();
                        if (max == min) {
                            return 1;
                        }
                        double dx = 1.0 / (n - 1);
                        double length = 0;
                        for (int k = 1; k < n; k++) {
                            double dy = (y[k] - y[k - 1]) / (max - min);
                            length += Math.sqrt(dy * dy + dx * dx);
                        }
                        return 1 + Math.log(length) / Math.log(2.0 * (n - 1));
                    }));
                });
    }

    /** Last n values ending at i, oldest first; null until n values exist. */
    private static double[] window(Indicator<Num> source, int i, int n, int begin) {
        int first = i - n + 1;
        if (first < begin) {
            return null;
        }
        double[] values = new double[n];
        for (int k = 0; k < n; k++) {
            values[k] = v(source, first + k);
        }
        return values;
    }

    /** Pearson correlation of a[aFrom..aFrom+count) and b[bFrom..bFrom+count); 0 when one side is flat. */
    private static double correlation(double[] a, int aFrom, double[] b, int bFrom, int count) {
        double meanA = 0;
        double meanB = 0;
        for (int k = 0; k < count; k++) {
            meanA += a[aFrom + k] / count;
            meanB += b[bFrom + k] / count;
        }
        double covariance = 0;
        double varianceA = 0;
        double varianceB = 0;
        for (int k = 0; k < count; k++) {
            double da = a[aFrom + k] - meanA;
            double db = b[bFrom + k] - meanB;
            covariance += da * db;
            varianceA += da * da;
            varianceB += db * db;
        }
        return varianceA == 0 || varianceB == 0 ? 0 : covariance / Math.sqrt(varianceA * varianceB);
    }

    /** Log return over n bars (pandas-ta {@code log_return}): ln(x / x n bars ago); NaN for non-positive input. */
    static IndicatorDefinition logReturn() {
        return define(LOG_RETURN).description("Log return").family(STATISTICAL).models(WINDOW)
                .params(period(1), source(PriceField.CLOSE)).outputs(OutputDef.value("ln(x / x[n])"))
                .lookback(s -> period(s)).warmup(s -> period(s) + 1)
                .build((c, s) -> {
                    Indicator<Num> source = source(c, s);
                    int begin = c.series().getBeginIndex();
                    int n = period(s);
                    return single(lambda(c, s, VALUE, (i, self) -> {
                        if (i - n < begin) {
                            return Double.NaN;
                        }
                        double now = v(source, i);
                        double before = v(source, i - n);
                        return now > 0 && before > 0 ? Math.log(now / before) : Double.NaN;
                    }));
                });
    }
}
