package com.rmrx.marketanalyzer.marketdata.service.candle;

import com.rmrx.marketanalyzer.marketdata.model.Candle;

import java.util.List;

/** Storage of candles. Writes are idempotent upserts keyed by {@code (exchange, symbol, timeframe, openTime)}. */
public interface CandleRepository {

    /**
     * Inserts the candle, or updates it if a candle with the same key exists with different values.
     *
     * @return {@code true} if a row was inserted or changed, {@code false} if an identical candle was already stored
     */
    boolean upsert(Candle candle);

    /**
     * Upserts many candles of ONE series (same exchange, symbol, timeframe) in a single statement.
     *
     * @return only the candles that were inserted ({@code inserted = true}) or whose values changed ({@code false});
     *         identical candles already stored are not returned
     */
    List<UpsertedCandle> upsertAll(List<Candle> candles, CandleSource source);

    /**
     * Upserts derived candles (source {@code DERIVED}) from the Flink aggregator, partial ({@code closed = false}) or
     * final, possibly of several series, in one statement. Several updates of the same candle: the last one wins.
     * A partial update never overwrites a closed candle.
     *
     * @return the candles whose row was inserted or changed (values or {@code closed}), in input order
     */
    List<DerivedCandle> upsertDerivedAll(List<DerivedCandle> candles);
}
