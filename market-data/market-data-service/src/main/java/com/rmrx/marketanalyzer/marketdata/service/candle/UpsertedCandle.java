package com.rmrx.marketanalyzer.marketdata.service.candle;

import com.rmrx.marketanalyzer.marketdata.model.Candle;

/**
 * A candle written by a batch upsert.
 *
 * @param inserted {@code true} = new row, {@code false} = existing row whose values changed
 */
public record UpsertedCandle(Candle candle, boolean inserted) {
}
