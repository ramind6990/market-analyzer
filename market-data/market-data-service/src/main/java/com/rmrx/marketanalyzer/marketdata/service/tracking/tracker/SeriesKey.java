package com.rmrx.marketanalyzer.marketdata.service.tracking.tracker;

import com.rmrx.marketanalyzer.marketdata.model.Timeframe;

import java.util.Objects;

/** Identity of one candle series. */
public record SeriesKey(String exchange, String symbol, Timeframe timeframe) {

    public SeriesKey {
        Objects.requireNonNull(exchange, "exchange");
        Objects.requireNonNull(symbol, "symbol");
        Objects.requireNonNull(timeframe, "timeframe");
    }

    @Override
    public String toString() {
        return exchange + ":" + symbol + ":" + timeframe;
    }
}
