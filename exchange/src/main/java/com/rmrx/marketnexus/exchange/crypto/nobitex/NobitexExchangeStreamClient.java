package com.rmrx.marketnexus.exchange.crypto.nobitex;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.rmrx.marketnexus.exchange.client.ExchangeCandleListener;
import com.rmrx.marketnexus.exchange.client.ExchangeStreamClient;
import com.rmrx.marketnexus.exchange.config.ExchangeConfig;
import com.rmrx.marketnexus.exchange.crypto.nobitex.model.NobitexCandle;
import com.rmrx.marketnexus.exchange.crypto.nobitex.model.NobitexResolution;
import com.rmrx.marketnexus.exchange.crypto.nobitex.ws.NobitexCandleListener;
import com.rmrx.marketnexus.exchange.crypto.nobitex.ws.NobitexWebSocketClient;
import com.rmrx.marketnexus.exchange.crypto.nobitex.ws.NobitexWebSocketConfig;
import com.rmrx.marketnexus.exchange.model.ExchangeCandle;
import com.rmrx.marketnexus.exchange.model.ExchangeSymbol;
import com.rmrx.marketnexus.exchange.model.ExchangeTimeframe;
import com.rmrx.marketnexus.exchange.model.ExchangeType;

import java.util.Objects;

/** {@link ExchangeStreamClient} for Nobitex: maps the generic contract onto the Nobitex WebSocket client. */
public final class NobitexExchangeStreamClient implements ExchangeStreamClient {

    private static final Logger log = LoggerFactory.getLogger(NobitexExchangeStreamClient.class);

    private final NobitexWebSocketClient webSocket;

    public NobitexExchangeStreamClient(ExchangeConfig config) {
        this(new NobitexWebSocketClient(toWebSocketConfig(config)));
    }

    NobitexExchangeStreamClient(NobitexWebSocketClient webSocket) {
        this.webSocket = Objects.requireNonNull(webSocket, "webSocket");
    }

    @Override
    public ExchangeType exchangeType() {
        return ExchangeType.CRYPTO_NOBITEX;
    }

    @Override
    public void connect() {
        webSocket.connect();
    }

    @Override
    public void subscribeCandles(ExchangeSymbol symbol, ExchangeTimeframe timeframe, ExchangeCandleListener listener) {
        Objects.requireNonNull(symbol, "symbol");
        Objects.requireNonNull(timeframe, "timeframe");
        Objects.requireNonNull(listener, "listener");
        NobitexResolution resolution = NobitexExchangeMapper.toNobitexResolution(timeframe);
        webSocket.subscribeCandles(NobitexExchangeMapper.toNobitexSymbol(symbol), resolution, new NobitexCandleListener() {
            @Override
            public void onUpdate(NobitexCandle forming) {
                deliver(() -> listener.onCandleUpdate(symbol, timeframe, toExchangeCandle(forming, false)));
            }

            @Override
            public void onClosed(NobitexCandle closed) {
                deliver(() -> listener.onCandleClosed(symbol, timeframe, toExchangeCandle(closed, true)));
            }
        });
    }

    @Override
    public void close() {
        webSocket.close();
    }

    /** Listener exceptions must never reach the WebSocket SDK (it would drop the connection). */
    private static void deliver(Runnable callback) {
        try {
            callback.run();
        } catch (RuntimeException e) {
            log.warn("Candle listener failed", e);
        }
    }

    private static ExchangeCandle toExchangeCandle(NobitexCandle candle, boolean closed) {
        return new ExchangeCandle(candle.openTime(), candle.closeTime(), candle.open(), candle.high(),
                candle.low(), candle.close(), candle.volume(), closed);
    }

    private static NobitexWebSocketConfig toWebSocketConfig(ExchangeConfig config) {
        Objects.requireNonNull(config, "config");
        if (config.exchangeType() != ExchangeType.CRYPTO_NOBITEX) {
            throw new IllegalArgumentException("Expected " + ExchangeType.CRYPTO_NOBITEX + " config, got "
                    + config.exchangeType());
        }
        if (config.websocketUrl() == null) {
            throw new IllegalArgumentException("websocketUrl is required for the Nobitex stream client");
        }
        // Public channels need no connection token; private channels will get one from /auth/ws/token/ later.
        return new NobitexWebSocketConfig(config.websocketUrl(), config.userAgent());
    }
}
