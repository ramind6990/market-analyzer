package com.rmrx.marketanalyzer.exchange.crypto.binance;

import com.rmrx.marketanalyzer.exchange.crypto.binance.model.BinanceErrorResponse;

import java.util.Optional;

/** A Binance API call failed: non-success HTTP status or unreadable response. */
public class BinanceApiException extends RuntimeException {

    private final int httpStatus;
    private final BinanceErrorResponse error;

    public BinanceApiException(int httpStatus, BinanceErrorResponse error, String message) {
        this(httpStatus, error, message, null);
    }

    public BinanceApiException(int httpStatus, BinanceErrorResponse error, String message, Throwable cause) {
        super(message, cause);
        this.httpStatus = httpStatus;
        this.error = error;
    }

    /** HTTP status of the response; {@code 0} when no response was received. */
    public int httpStatus() {
        return httpStatus;
    }

    /** Parsed error body, when the server sent one in the documented format. */
    public Optional<BinanceErrorResponse> error() {
        return Optional.ofNullable(error);
    }
}
