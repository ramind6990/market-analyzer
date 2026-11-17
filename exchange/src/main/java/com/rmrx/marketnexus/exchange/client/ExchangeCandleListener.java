package com.rmrx.marketnexus.exchange.client;

import com.rmrx.marketnexus.exchange.model.ExchangeCandle;
import com.rmrx.marketnexus.exchange.model.ExchangeSymbol;
import com.rmrx.marketnexus.exchange.model.ExchangeTimeframe;

/**
 * Receives candles from an {@link ExchangeStreamClient} subscription.
 * Called on the stream client's event thread: return quickly, do not block, do not throw.
 */
public interface ExchangeCandleListener {

    /**
     * A candle that has finished, as far as the stream can tell. Depending on the exchange this may be provisional
     * (e.g. Nobitex streams snapshots, so trades in a candle's last seconds can be missing); REST data is authoritative.
     */
    void onCandleClosed(ExchangeSymbol symbol, ExchangeTimeframe timeframe, ExchangeCandle candle);

    /** The still-forming candle. Optional; ignored by default. */
    default void onCandleUpdate(ExchangeSymbol symbol, ExchangeTimeframe timeframe, ExchangeCandle candle) {
    }
}
