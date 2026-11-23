package com.rmrx.marketnexus.exchange.client;

import com.rmrx.marketnexus.exchange.config.ExchangeConfig;
import com.rmrx.marketnexus.exchange.crypto.binance.BinanceExchangeClient;
import com.rmrx.marketnexus.exchange.crypto.binance.BinanceExchangeStreamClient;
import com.rmrx.marketnexus.exchange.crypto.nobitex.NobitexExchangeClient;
import com.rmrx.marketnexus.exchange.crypto.nobitex.NobitexExchangeStreamClient;
import com.rmrx.marketnexus.exchange.model.ExchangeType;

import java.util.Objects;

/** Creates the {@link ExchangeClient} implementation for a config's {@link ExchangeType}. */
public final class ExchangeClients {

    private ExchangeClients() {
    }

    public static ExchangeClient create(ExchangeConfig config) {
        Objects.requireNonNull(config, "config");
        return switch (config.exchangeType()) {
            case CRYPTO_NOBITEX -> new NobitexExchangeClient(config);
            case CRYPTO_BINANCE -> new BinanceExchangeClient(config);
        };
    }

    /** Creates the streaming (WebSocket) client; the config must contain a {@code websocketUrl}. */
    public static ExchangeStreamClient createStream(ExchangeConfig config) {
        Objects.requireNonNull(config, "config");
        return switch (config.exchangeType()) {
            case CRYPTO_NOBITEX -> new NobitexExchangeStreamClient(config);
            case CRYPTO_BINANCE -> new BinanceExchangeStreamClient(config);
        };
    }
}
