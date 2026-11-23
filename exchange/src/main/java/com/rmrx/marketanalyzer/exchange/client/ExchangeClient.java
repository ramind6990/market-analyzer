package com.rmrx.marketanalyzer.exchange.client;

import com.rmrx.marketanalyzer.exchange.model.ExchangeException;
import com.rmrx.marketanalyzer.exchange.model.ExchangeOhlcRequest;
import com.rmrx.marketanalyzer.exchange.model.ExchangeOhlcResponse;
import com.rmrx.marketanalyzer.exchange.model.ExchangeType;

/**
 * Entry point for other modules to talk to an exchange, independent of which exchange it is.
 * Obtain one with {@link ExchangeClients#create(com.rmrx.marketanalyzer.exchange.config.ExchangeConfig)}.
 * More operations (order book, orders, balances, ...) will be added here as they are needed.
 */
public interface ExchangeClient extends AutoCloseable {

    ExchangeType exchangeType();

    /**
     * Fetches OHLCV candles.
     *
     * @throws ExchangeException when the exchange rejects the request or cannot be reached
     */
    ExchangeOhlcResponse getOhlc(ExchangeOhlcRequest request);

    /** Releases HTTP resources. */
    @Override
    void close();
}
