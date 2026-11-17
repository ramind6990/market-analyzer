package com.rmrx.marketnexus.exchange.client;

import com.rmrx.marketnexus.exchange.model.ExchangeSymbol;
import com.rmrx.marketnexus.exchange.model.ExchangeTimeframe;
import com.rmrx.marketnexus.exchange.model.ExchangeType;

/**
 * Streaming (WebSocket) access to an exchange, independent of which exchange it is.
 * Obtain one with {@link ExchangeClients#createStream(com.rmrx.marketnexus.exchange.config.ExchangeConfig)}.
 * Implementations reconnect and resubscribe on their own after connection losses.
 */
public interface ExchangeStreamClient extends AutoCloseable {

    ExchangeType exchangeType();

    /** Starts the connection. Subscriptions can be made before or after connecting. */
    void connect();

    /**
     * Subscribes to candles of one symbol and timeframe.
     *
     * @throws com.rmrx.marketnexus.exchange.model.ExchangeException if the exchange does not support the timeframe
     */
    void subscribeCandles(ExchangeSymbol symbol, ExchangeTimeframe timeframe, ExchangeCandleListener listener);

    /** Disconnects and releases resources. */
    @Override
    void close();
}
