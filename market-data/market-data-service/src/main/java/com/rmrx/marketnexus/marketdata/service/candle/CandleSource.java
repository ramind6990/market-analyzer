package com.rmrx.marketnexus.marketdata.service.candle;

/** Where a stored candle came from (database enum {@code candle_source}). */
public enum CandleSource {
    /** Live exchange stream (WebSocket). */
    STREAM,
    /** Fetched in a batch over the REST API (reconciliation). */
    POLL,
    /** Aggregated from smaller candles. */
    DERIVED
}
