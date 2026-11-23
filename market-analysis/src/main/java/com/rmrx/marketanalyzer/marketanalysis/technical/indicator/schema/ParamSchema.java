package com.rmrx.marketanalyzer.marketanalysis.technical.indicator.schema;

import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.spec.IndicatorSpec;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.spec.ParamValue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Parameters of one indicator type. Validates a spec and fills in defaults. */
public final class ParamSchema {

    public static final ParamSchema EMPTY = new ParamSchema(List.of());

    private final Map<String, ParamDef> params = new LinkedHashMap<>();

    public ParamSchema(List<ParamDef> params) {
        for (ParamDef def : params) {
            if (this.params.put(def.name(), def) != null) {
                throw new IllegalArgumentException("duplicate parameter " + def.name());
            }
        }
    }

    public static ParamSchema of(ParamDef... params) {
        return new ParamSchema(List.of(params));
    }

    public List<ParamDef> params() {
        return List.copyOf(params.values());
    }

    /**
     * Returns the spec with every parameter present, coerced and range-checked.
     * Nested indicator sources are not resolved here (the catalog does that).
     */
    public IndicatorSpec canonicalize(IndicatorSpec spec) {
        for (String name : spec.params().keySet()) {
            if (!params.containsKey(name)) {
                throw new IndicatorSpecException(spec.type() + ": unknown parameter " + name);
            }
        }
        TreeMap<String, ParamValue> values = new TreeMap<>();
        for (ParamDef def : params.values()) {
            ParamValue value = spec.params().get(def.name());
            if (value == null) {
                value = def.defaultValue();
                if (value == null) {
                    throw new IndicatorSpecException(spec.type() + ": missing parameter " + def.name());
                }
            }
            values.put(def.name(), def.coerce(spec.type(), value));
        }
        return IndicatorSpec.of(spec.type(), values);
    }
}
