package com.rmrx.marketanalyzer.marketanalysis.technical.indicator.definition;

import java.util.Objects;

/**
 * One output line of an indicator type. Every output is computed, stored and seeded. INTERNAL outputs hold hidden
 * recursive state (e.g. RSI's average gain) that is needed to continue the computation but is not meant for strategies.
 */
public record OutputDef(String name, Visibility visibility, String description) {

    public static final String VALUE = "value";

    public enum Visibility {
        PUBLIC,
        INTERNAL
    }

    public OutputDef {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(visibility, "visibility");
    }

    public static OutputDef value(String description) {
        return publicOutput(VALUE, description);
    }

    public static OutputDef publicOutput(String name, String description) {
        return new OutputDef(name, Visibility.PUBLIC, description);
    }

    public static OutputDef internal(String name, String description) {
        return new OutputDef(name, Visibility.INTERNAL, description);
    }

    public boolean isPublic() {
        return visibility == Visibility.PUBLIC;
    }
}
