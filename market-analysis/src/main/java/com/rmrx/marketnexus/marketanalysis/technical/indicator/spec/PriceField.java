package com.rmrx.marketnexus.marketanalysis.technical.indicator.spec;

/** Raw and derived candle prices usable as indicator input. Never stored; always read from candles. */
public enum PriceField implements Source {
    OPEN,
    HIGH,
    LOW,
    CLOSE,
    VOLUME,
    /** (high + low) / 2 */
    HL2,
    /** typical price: (high + low + close) / 3 */
    HLC3,
    /** (open + high + low + close) / 4 */
    OHLC4;

    @Override
    public String canonical() {
        return name();
    }
}
