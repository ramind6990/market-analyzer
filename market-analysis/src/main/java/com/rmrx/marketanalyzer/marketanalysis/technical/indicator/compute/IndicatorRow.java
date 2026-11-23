package com.rmrx.marketanalyzer.marketanalysis.technical.indicator.compute;

import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;

/**
 * All node values of one series at one bar, in layout order. Storage-agnostic: the same shape is read back as seed.
 *
 * @param openTime      open time of the bar
 * @param layoutVersion version of the layout the values follow
 * @param values        one value per layout entry: {@code values[i]} belongs to {@code layout.entries().get(i)};
 *                      find one with {@code layout.indexOf(outputRef)}
 */
public record IndicatorRow(Instant openTime, long layoutVersion, double[] values) {

    public IndicatorRow {
        Objects.requireNonNull(openTime, "openTime");
        Objects.requireNonNull(values, "values");
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof IndicatorRow other && openTime.equals(other.openTime)
                && layoutVersion == other.layoutVersion && Arrays.equals(values, other.values);
    }

    @Override
    public int hashCode() {
        return Objects.hash(openTime, layoutVersion, Arrays.hashCode(values));
    }

    @Override
    public String toString() {
        return "IndicatorRow[" + openTime + ", v" + layoutVersion + ", " + Arrays.toString(values) + "]";
    }
}
