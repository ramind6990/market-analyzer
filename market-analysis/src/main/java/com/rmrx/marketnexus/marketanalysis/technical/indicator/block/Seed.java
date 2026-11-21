package com.rmrx.marketnexus.marketanalysis.technical.indicator.block;

import java.util.Arrays;

/**
 * Stored values of one node for the bars {@code firstIndex..lastIndex} of the job's bar series.
 * <p>
 * A seeded indicator returns these values instead of computing, so ta4j's recursion
 * ({@code getValue(i - 1)}) stops at {@link #lastIndex()}. Asking for an index below {@link #firstIndex()} but not
 * above {@link #lastIndex()} means a definition declared a too small lookback: that is a bug, so it fails loudly.
 */
public final class Seed {

    private static final Seed NONE = new Seed(0, new double[0]);

    private final int firstIndex;
    private final double[] values;

    private Seed(int firstIndex, double[] values) {
        this.firstIndex = firstIndex;
        this.values = values;
    }

    /** No stored values: everything is computed from candles. */
    public static Seed none() {
        return NONE;
    }

    /** {@code values[k]} is the stored value at bar index {@code firstIndex + k}. */
    public static Seed of(int firstIndex, double[] values) {
        if (firstIndex < 0) {
            throw new IllegalArgumentException("firstIndex < 0");
        }
        return new Seed(firstIndex, values.clone());
    }

    public int firstIndex() {
        return firstIndex;
    }

    /** Last seeded bar index, {@code -1} when there is no seed. */
    public int lastIndex() {
        return firstIndex + values.length - 1;
    }

    public boolean isEmpty() {
        return values.length == 0;
    }

    /** True when the value at {@code index} comes from the seed and must not be computed. */
    public boolean covers(int index) {
        if (index > lastIndex()) {
            return false;
        }
        if (index < firstIndex) {
            throw new IllegalStateException("seed starts at bar " + firstIndex + " but bar " + index
                    + " was requested: declared lookback too small");
        }
        return true;
    }

    public double value(int index) {
        return values[index - firstIndex];
    }

    @Override
    public String toString() {
        return isEmpty() ? "Seed[none]" : "Seed[" + firstIndex + ".." + lastIndex() + "]=" + Arrays.toString(values);
    }
}
