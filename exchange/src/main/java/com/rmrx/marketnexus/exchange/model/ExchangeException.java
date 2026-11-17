package com.rmrx.marketnexus.exchange.model;

import java.util.Optional;

/** An exchange call failed. Exchange-specific exceptions are translated into this one at the contract boundary. */
public class ExchangeException extends RuntimeException {

    private final ExchangeType exchangeType;
    private final int httpStatus;
    private final String errorCode;

    public ExchangeException(ExchangeType exchangeType, int httpStatus, String errorCode, String message,
                             Throwable cause) {
        super(message, cause);
        this.exchangeType = exchangeType;
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
    }

    public ExchangeException(ExchangeType exchangeType, String message) {
        this(exchangeType, 0, null, message, null);
    }

    public ExchangeType exchangeType() {
        return exchangeType;
    }

    /** HTTP status of the failed response; {@code 0} when there was none (e.g. a network error). */
    public int httpStatus() {
        return httpStatus;
    }

    /** Exchange-specific error code, when the exchange returned one (e.g. Nobitex {@code InvalidSymbol}). */
    public Optional<String> errorCode() {
        return Optional.ofNullable(errorCode);
    }
}
