package com.rmrx.marketanalyzer.marketanalysis.technical.indicator.schema;

/** A spec does not match its definition: unknown type or parameter, wrong kind, value out of range, cycle. */
public class IndicatorSpecException extends IllegalArgumentException {

    public IndicatorSpecException(String message) {
        super(message);
    }
}
