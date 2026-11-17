package com.rmrx.marketnexus.marketdata.service.candle;

import com.rmrx.marketnexus.marketdata.model.Candle;

/** Storage of candles. Writes are idempotent upserts keyed by {@code (exchange, symbol, timeframe, openTime)}. */
public interface CandleRepository {

    /**
     * Inserts the candle, or updates it if a candle with the same key exists with different values.
     *
     * @return {@code true} if a row was inserted or changed, {@code false} if an identical candle was already stored
     */
    boolean upsert(Candle candle);
}
