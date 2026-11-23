package com.rmrx.marketnexus.exchange.crypto.binance.ws;

import com.rmrx.marketnexus.exchange.crypto.binance.model.BinanceKline;

/** Receives klines of one stream. Called on the WebSocket's delivery thread, one message at a time. */
public interface BinanceCandleListener {

    /** The kline is still forming (Binance pushes about every 2 s). */
    void onUpdate(BinanceKline forming);

    /** The kline is final (event field {@code x = true}). */
    void onClosed(BinanceKline closed);
}
