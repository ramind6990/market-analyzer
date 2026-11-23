package com.rmrx.marketanalyzer.marketanalysis.api;

import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.catalog.IndicatorCatalog;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.schema.IndicatorSpecException;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.schema.ParamDef;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.spec.IndicatorSpec;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.spec.ParamValue;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.spec.PriceField;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Converts API input (type + text parameters) into an indicator spec and an effective spec back into API parameters.
 * Indicator-on-indicator sources are not accepted through the API (only price fields).
 */
@Component
class IndicatorSpecParser {

    private final IndicatorCatalog catalog;

    IndicatorSpecParser(IndicatorCatalog catalog) {
        this.catalog = catalog;
    }

    IndicatorSpec parse(String type, Map<String, String> params) {
        String normalizedType = type.trim().toUpperCase();
        Map<String, ParamDef> defs = new LinkedHashMap<>();
        for (ParamDef def : catalog.definition(normalizedType).paramSchema().params()) {
            defs.put(def.name(), def);
        }
        Map<String, ParamValue> values = new TreeMap<>();
        params.forEach((name, text) -> {
            ParamDef def = defs.get(name);
            if (def == null) {
                throw new IndicatorSpecException(normalizedType + ": unknown parameter " + name);
            }
            values.put(name, value(normalizedType, def, text.trim()));
        });
        return IndicatorSpec.of(normalizedType, values);
    }

    /** Effective parameters for the response: numbers as numbers, everything else as text. */
    static Map<String, Object> params(IndicatorSpec spec) {
        Map<String, Object> result = new LinkedHashMap<>();
        spec.params().forEach((name, value) -> result.put(name, switch (value) {
            case ParamValue.IntParam p -> p.value();
            case ParamValue.DecimalParam p -> p.value();
            default -> value.canonical();
        }));
        return result;
    }

    private static ParamValue value(String type, ParamDef def, String text) {
        try {
            return switch (def.kind()) {
                case INT, DECIMAL -> ParamValue.of(new BigDecimal(text));
                case ENUM -> new ParamValue.EnumParam(text.toUpperCase());
                case SOURCE -> new ParamValue.SourceParam(PriceField.valueOf(text.toUpperCase()));
            };
        } catch (IllegalArgumentException e) {
            throw new IndicatorSpecException(type + ": invalid value '" + text + "' for parameter " + def.name());
        }
    }
}
