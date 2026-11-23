package com.rmrx.marketanalyzer.marketanalysis.technical.indicator.schema;

import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.spec.ParamValue;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.spec.ParamValue.DecimalParam;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.spec.ParamValue.EnumParam;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.spec.ParamValue.IntParam;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.spec.ParamValue.SourceParam;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.spec.Source;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * One parameter of an indicator type: name, kind, default and allowed values.
 *
 * @param defaultValue used when the spec does not set the parameter; {@code null} = required
 * @param min          inclusive lower bound for INT/DECIMAL, {@code null} = none
 * @param max          inclusive upper bound for INT/DECIMAL, {@code null} = none
 * @param allowed      allowed names for ENUM, empty otherwise
 */
public record ParamDef(String name, ParamKind kind, ParamValue defaultValue, BigDecimal min, BigDecimal max,
                       Set<String> allowed, String description) {

    public ParamDef {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(kind, "kind");
        allowed = Set.copyOf(allowed);
    }

    public static ParamDef integer(String name, int defaultValue, int min, int max, String description) {
        return new ParamDef(name, ParamKind.INT, ParamValue.of(defaultValue),
                BigDecimal.valueOf(min), BigDecimal.valueOf(max), Set.of(), description);
    }

    /** Bar count parameter with the usual range 1..1000. */
    public static ParamDef period(String name, int defaultValue, String description) {
        return integer(name, defaultValue, 1, 1000, description);
    }

    public static ParamDef decimal(String name, double defaultValue, double min, double max, String description) {
        return new ParamDef(name, ParamKind.DECIMAL, ParamValue.of(defaultValue),
                BigDecimal.valueOf(min), BigDecimal.valueOf(max), Set.of(), description);
    }

    public static <E extends Enum<E>> ParamDef enumeration(String name, Class<E> type, E defaultValue,
                                                          String description) {
        Set<String> names = Arrays.stream(type.getEnumConstants()).map(Enum::name).collect(Collectors.toSet());
        return new ParamDef(name, ParamKind.ENUM, ParamValue.of(defaultValue), null, null, names, description);
    }

    public static ParamDef source(String name, Source defaultValue, String description) {
        return new ParamDef(name, ParamKind.SOURCE, ParamValue.of(defaultValue), null, null, Set.of(), description);
    }

    /** Checks the value and converts it to this parameter's kind (e.g. int given for a decimal parameter). */
    ParamValue coerce(String type, ParamValue value) {
        ParamValue coerced = switch (kind) {
            case INT -> switch (value) {
                case IntParam p -> p;
                case DecimalParam p when isIntegral(p.value()) -> ParamValue.of(p.value().intValueExact());
                default -> throw invalid(type, value, "expected an integer");
            };
            case DECIMAL -> switch (value) {
                case DecimalParam p -> p;
                case IntParam p -> ParamValue.of(BigDecimal.valueOf(p.value()));
                default -> throw invalid(type, value, "expected a decimal");
            };
            case ENUM -> {
                if (value instanceof EnumParam p && allowed.contains(p.value())) {
                    yield p;
                }
                throw invalid(type, value, "expected one of " + allowed);
            }
            case SOURCE -> {
                if (value instanceof SourceParam p) {
                    yield p;
                }
                throw invalid(type, value, "expected a price field or an indicator output");
            }
        };
        checkRange(type, coerced);
        return coerced;
    }

    private void checkRange(String type, ParamValue value) {
        BigDecimal number = switch (value) {
            case IntParam p -> BigDecimal.valueOf(p.value());
            case DecimalParam p -> p.value();
            default -> null;
        };
        if (number == null) {
            return;
        }
        if (min != null && number.compareTo(min) < 0 || max != null && number.compareTo(max) > 0) {
            throw invalid(type, value, "allowed range " + min + ".." + max);
        }
    }

    private static boolean isIntegral(BigDecimal value) {
        return value.signum() == 0 || value.stripTrailingZeros().scale() <= 0;
    }

    private IndicatorSpecException invalid(String type, ParamValue value, String reason) {
        return new IndicatorSpecException(type + ": invalid " + name + "=" + value.canonical() + " (" + reason + ")");
    }
}
