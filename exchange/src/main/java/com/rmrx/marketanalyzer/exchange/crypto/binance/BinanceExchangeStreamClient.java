package com.rmrx.marketanalyzer.exchange.crypto.binance;

import com.rmrx.marketanalyzer.exchange.client.ExchangeCandleListener;
import com.rmrx.marketanalyzer.exchange.client.ExchangeStreamClient;
import com.rmrx.marketanalyzer.exchange.config.ExchangeConfig;
import com.rmrx.marketanalyzer.exchange.crypto.binance.model.BinanceInterval;
import com.rmrx.marketanalyzer.exchange.crypto.binance.model.BinanceKline;
import com.rmrx.marketanalyzer.exchange.crypto.binance.ws.BinanceCandleListener;
import com.rmrx.marketanalyzer.exchange.crypto.binance.ws.BinanceWebSocketClient;
import com.rmrx.marketanalyzer.exchange.crypto.binance.ws.BinanceWebSocketConfig;
import com.rmrx.marketanalyzer.exchange.model.ExchangeSymbol;
import com.rmrx.marketanalyzer.exchange.model.ExchangeTimeframe;
import com.rmrx.marketanalyzer.exchange.model.ExchangeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/** {@link ExchangeStreamClient} for Binance spot: maps the generic contract onto the Binance WebSocket client. */
public final class BinanceExchangeStreamClient implements ExchangeStreamClient {

    private static final Logger log = LoggerFactory.getLogger(BinanceExchangeStreamClient.class);

    private final BinanceWebSocketClient webSocket;

    public BinanceExchangeStreamClient(ExchangeConfig config) {
        this(new BinanceWebSocketClient(toWebSocketConfig(config)));
    }

    BinanceExchangeStreamClient(BinanceWebSocketClient webSocket) {
        this.webSocket = Objects.requireNonNull(webSocket, "webSocket");
    }

    @Override
    public ExchangeType exchangeType() {
        return ExchangeType.CRYPTO_BINANCE;
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
        BinanceInterval interval = BinanceExchangeMapper.toBinanceInterval(timeframe);
        webSocket.subscribeCandles(BinanceExchangeMapper.toBinanceSymbol(symbol), interval, new BinanceCandleListener() {
            @Override
            public void onUpdate(BinanceKline forming) {
                deliver(() -> listener.onCandleUpdate(symbol, timeframe,
                        BinanceExchangeMapper.toExchangeCandle(forming, false)));
            }

            @Override
            public void onClosed(BinanceKline closed) {
                deliver(() -> listener.onCandleClosed(symbol, timeframe,
                        BinanceExchangeMapper.toExchangeCandle(closed, true)));
            }
        });
    }

    @Override
    public void close() {
        webSocket.close();
    }

    /** Listener exceptions must never reach the WebSocket (it would drop the connection). */
    private static void deliver(Runnable callback) {
        try {
            callback.run();
        } catch (RuntimeException e) {
            log.warn("Candle listener failed", e);
        }
    }

    private static BinanceWebSocketConfig toWebSocketConfig(ExchangeConfig config) {
        BinanceExchangeMapper.requireBinance(config);
        if (config.websocketUrl() == null) {
            throw new IllegalArgumentException("websocketUrl is required for the Binance stream client");
        }
        return new BinanceWebSocketConfig(config.websocketUrl(), config.userAgent(), config.connectTimeout());
    }
}
