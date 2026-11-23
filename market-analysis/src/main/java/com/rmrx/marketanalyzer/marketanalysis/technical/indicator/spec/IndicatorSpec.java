package com.rmrx.marketanalyzer.marketanalysis.technical.indicator.spec;

import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.spec.ParamValue.DecimalParam;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.spec.ParamValue.EnumParam;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.spec.ParamValue.IntParam;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.spec.ParamValue.SourceParam;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Which indicator with which parameters, e.g. {@code EMA(period=20,source=CLOSE)}.
 * <p>
 * Immutable value object. Equality, hashing and ordering use the canonical string, so the same indicator requested
 * twice is one node of the indicator graph. A spec is only fully canonical (defaults filled in, values coerced)
 * after {@code IndicatorCatalog.canonicalize}.
 */
public final class IndicatorSpec implements Comparable<IndicatorSpec> {

    private static final Pattern TYPE_PATTERN = Pattern.compile("[A-Z][A-Z0-9_]*");
    private static final Pattern PARAM_PATTERN = Pattern.compile("[a-z][a-zA-Z0-9]*");

    private final String type;
    private final SortedMap<String, ParamValue> params;
    private final String canonical;

    private IndicatorSpec(String type, SortedMap<String, ParamValue> params) {
        this.type = type;
        this.params = Collections.unmodifiableSortedMap(params);
        this.canonical = type + params.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue().canonical())
                .collect(Collectors.joining(",", "(", ")"));
    }

    public static IndicatorSpec of(String type) {
        return of(type, Map.of());
    }

    public static IndicatorSpec of(String type, Map<String, ? extends ParamValue> params) {
        Objects.requireNonNull(type, "type");
        if (!TYPE_PATTERN.matcher(type).matches()) {
            throw new IllegalArgumentException("invalid indicator type: " + type);
        }
        TreeMap<String, ParamValue> copy = new TreeMap<>();
        params.forEach((name, value) -> {
            if (!PARAM_PATTERN.matcher(name).matches()) {
                throw new IllegalArgumentException("invalid parameter name: " + name);
            }
            copy.put(name, Objects.requireNonNull(value, name));
        });
        return new IndicatorSpec(type, copy);
    }

    public IndicatorSpec with(String name, ParamValue value) {
        TreeMap<String, ParamValue> copy = new TreeMap<>(params);
        copy.put(name, value);
        return of(type, copy);
    }

    public IndicatorSpec with(String name, int value) {
        return with(name, ParamValue.of(value));
    }

    public IndicatorSpec with(String name, double value) {
        return with(name, ParamValue.of(value));
    }

    public IndicatorSpec with(String name, BigDecimal value) {
        return with(name, ParamValue.of(value));
    }

    public IndicatorSpec with(String name, Source value) {
        return with(name, ParamValue.of(value));
    }

    /** A price field is a source (not a plain enum parameter). */
    public IndicatorSpec with(String name, PriceField value) {
        return with(name, (Source) value);
    }

    public IndicatorSpec with(String name, Enum<?> value) {
        return with(name, ParamValue.of(value));
    }

    public String type() {
        return type;
    }

    public SortedMap<String, ParamValue> params() {
        return params;
    }

    public String canonical() {
        return canonical;
    }

    public OutputRef output(String name) {
        return new OutputRef(this, name);
    }

    public int intParam(String name) {
        return switch (param(name)) {
            case IntParam p -> p.value();
            case DecimalParam p -> p.value().intValueExact();
            default -> throw wrongKind(name, "int");
        };
    }

    public double decimalParam(String name) {
        return switch (param(name)) {
            case DecimalParam p -> p.value().doubleValue();
            case IntParam p -> p.value();
            default -> throw wrongKind(name, "decimal");
        };
    }

    public Source sourceParam(String name) {
        if (param(name) instanceof SourceParam p) {
            return p.value();
        }
        throw wrongKind(name, "source");
    }

    public <E extends Enum<E>> E enumParam(String name, Class<E> enumType) {
        if (param(name) instanceof EnumParam p) {
            return Enum.valueOf(enumType, p.value());
        }
        throw wrongKind(name, "enum");
    }

    /** Outputs of other indicators used as input via source parameters (indicator on indicator). */
    public List<OutputRef> sourceRefs() {
        return params.values().stream()
                .filter(v -> v instanceof SourceParam p && p.value() instanceof OutputRef)
                .map(v -> (OutputRef) ((SourceParam) v).value())
                .toList();
    }

    private ParamValue param(String name) {
        ParamValue value = params.get(name);
        if (value == null) {
            throw new IllegalArgumentException(canonical + ": missing parameter " + name);
        }
        return value;
    }

    private IllegalArgumentException wrongKind(String name, String expected) {
        return new IllegalArgumentException(canonical + ": parameter " + name + " is not " + expected);
    }

    @Override
    public int compareTo(IndicatorSpec other) {
        return canonical.compareTo(other.canonical);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof IndicatorSpec other && canonical.equals(other.canonical);
    }

    @Override
    public int hashCode() {
        return canonical.hashCode();
    }

    @Override
    public String toString() {
        return canonical;
    }
}
