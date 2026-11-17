package com.rmrx.marketnexus.exchange.client;

import com.rmrx.marketnexus.exchange.config.ExchangeConfig;
import com.rmrx.marketnexus.exchange.crypto.nobitex.NobitexExchangeClient;
import com.rmrx.marketnexus.exchange.crypto.nobitex.NobitexExchangeStreamClient;
import com.rmrx.marketnexus.exchange.crypto.nobitex.NobitexGapMockStreamClient;
import com.rmrx.marketnexus.exchange.crypto.nobitex.NobitexMockStreamClient;
import com.rmrx.marketnexus.exchange.model.ExchangeType;

import java.time.Duration;
import java.util.Objects;

/** Creates the {@link ExchangeClient} implementation for a config's {@link ExchangeType}. */
public final class ExchangeClients {

    private ExchangeClients() {
    }

    public static ExchangeClient create(ExchangeConfig config) {
        Objects.requireNonNull(config, "config");
        return switch (config.exchangeType()) {
            case CRYPTO_NOBITEX -> new NobitexExchangeClient(config);
        };
    }

    /** Creates the streaming (WebSocket) client; the config must contain a {@code websocketUrl}. */
    public static ExchangeStreamClient createStream(ExchangeConfig config) {
        Objects.requireNonNull(config, "config");
        return switch (config.exchangeType()) {
            case CRYPTO_NOBITEX -> new NobitexExchangeStreamClient(config);
        };
    }

    /**
     * Creates a fake stream for the exchange, for running without a real connection: it emits generated closed
     * candles for every subscription, one every {@code emitInterval} (simulated time runs faster than real time).
     *
     * @param seed random seed, so the generated data can be reproduced
     */
    public static ExchangeStreamClient createMockStream(ExchangeType exchangeType, Duration emitInterval, long seed) {
        Objects.requireNonNull(exchangeType, "exchangeType");
        return switch (exchangeType) {
            case CRYPTO_NOBITEX -> new NobitexMockStreamClient(emitInterval, seed);
        };
    }

    /**
     * Creates a fake stream for testing reconciliation: on connect it emits, once, 100 closed candles per subscription
     * starting 8 hours ago, with 3–4 random gaps of 6–15 missing candles each (logged), then stays silent.
     *
     * @param seed random seed, so the same gaps can be reproduced
     */
    public static ExchangeStreamClient createGapMockStream(ExchangeType exchangeType, long seed) {
        Objects.requireNonNull(exchangeType, "exchangeType");
        return switch (exchangeType) {
            case CRYPTO_NOBITEX -> new NobitexGapMockStreamClient(seed);
        };
    }
}
