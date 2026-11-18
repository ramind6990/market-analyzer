package com.rmrx.marketnexus.marketdata.service.candle;

import com.rmrx.marketnexus.marketdata.model.Candle;

/**
 * A candle written by a batch upsert.
 *
 * @param inserted {@code true} = new row, {@code false} = existing row whose values changed
 */
public record UpsertedCandle(Candle candle, boolean inserted) {
}
