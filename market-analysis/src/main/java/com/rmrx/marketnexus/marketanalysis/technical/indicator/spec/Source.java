package com.rmrx.marketnexus.marketanalysis.technical.indicator.spec;

/**
 * Input series of an indicator: either a price field of the candle or an output of another indicator
 * (indicator on indicator, e.g. an EMA of an RSI).
 */
public sealed interface Source permits PriceField, OutputRef {

    /** Canonical text, part of the owning spec's canonical form. */
    String canonical();
}
