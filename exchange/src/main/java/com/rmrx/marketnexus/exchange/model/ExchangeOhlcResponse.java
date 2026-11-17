package com.rmrx.marketnexus.exchange.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * OHLCV candles returned by an exchange.
 *
 * @param exchangeType exchange the data came from
 * @param symbol       trading pair
 * @param timeframe    candle timeframe
 * @param candles      oldest first; empty when the exchange has no data for the range.
 *                     The newest candle may still be forming (see {@link ExchangeCandle#closed()}).
 * @param retrievedAt  when the response was received
 */
public record ExchangeOhlcResponse(
        ExchangeType exchangeType,
        ExchangeSymbol symbol,
        ExchangeTimeframe timeframe,
        List<ExchangeCandle> candles,
        Instant retrievedAt) {

    public ExchangeOhlcResponse {
        Objects.requireNonNull(exchangeType, "exchangeType");
        Objects.requireNonNull(symbol, "symbol");
        Objects.requireNonNull(timeframe, "timeframe");
        Objects.requireNonNull(retrievedAt, "retrievedAt");
        candles = candles == null ? List.of() : List.copyOf(candles);
    }

    /** Only the candles that had finished at {@code retrievedAt}. */
    public List<ExchangeCandle> closedCandles() {
        return candles.stream().filter(ExchangeCandle::closed).toList();
    }
}
