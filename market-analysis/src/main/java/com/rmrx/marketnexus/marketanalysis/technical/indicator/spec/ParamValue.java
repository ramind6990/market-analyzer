package com.rmrx.marketnexus.marketanalysis.technical.indicator.spec;

import java.math.BigDecimal;
import java.util.Objects;

/** Value of one indicator parameter. Each kind has a canonical text form used in the spec's canonical string. */
public sealed interface ParamValue {

    String canonical();

    static IntParam of(int value) {
        return new IntParam(value);
    }

    static DecimalParam of(BigDecimal value) {
        return new DecimalParam(value);
    }

    static DecimalParam of(double value) {
        return new DecimalParam(BigDecimal.valueOf(value));
    }

    static EnumParam of(Enum<?> value) {
        return new EnumParam(value.name());
    }

    static SourceParam of(Source value) {
        return new SourceParam(value);
    }

    record IntParam(int value) implements ParamValue {
        @Override
        public String canonical() {
            return Integer.toString(value);
        }
    }

    /** Decimal normalized so that {@code 2}, {@code 2.0} and {@code 2.00} are the same value. */
    record DecimalParam(BigDecimal value) implements ParamValue {
        public DecimalParam {
            Objects.requireNonNull(value, "value");
            value = value.signum() == 0 ? BigDecimal.ZERO : value.stripTrailingZeros();
        }

        @Override
        public String canonical() {
            return value.toPlainString();
        }
    }

    record EnumParam(String value) implements ParamValue {
        public EnumParam {
            Objects.requireNonNull(value, "value");
        }

        @Override
        public String canonical() {
            return value;
        }
    }

    record SourceParam(Source value) implements ParamValue {
        public SourceParam {
            Objects.requireNonNull(value, "value");
        }

        @Override
        public String canonical() {
            return value.canonical();
        }
    }
}
