package com.rmrx.marketnexus.exchange.crypto.binance.model;

/**
 * Error body returned by Binance, e.g. {@code {"code":-1121,"msg":"Invalid symbol."}}.
 *
 * @param code negative Binance error code; use this, not {@code msg}, for handling
 * @param msg  human-readable text, for debugging only
 */
public record BinanceErrorResponse(Integer code, String msg) {
}
