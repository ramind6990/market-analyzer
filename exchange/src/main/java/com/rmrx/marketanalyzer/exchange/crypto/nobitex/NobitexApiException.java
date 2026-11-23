package com.rmrx.marketanalyzer.exchange.crypto.nobitex;

import com.rmrx.marketanalyzer.exchange.crypto.nobitex.model.NobitexErrorResponse;

import java.util.Optional;

/** A Nobitex API call failed: non-success HTTP status, unreadable response, or an error reported in the body. */
public class NobitexApiException extends RuntimeException {

    private final int httpStatus;
    private final NobitexErrorResponse error;

    public NobitexApiException(int httpStatus, NobitexErrorResponse error, String message) {
        this(httpStatus, error, message, null);
    }

    public NobitexApiException(int httpStatus, NobitexErrorResponse error, String message, Throwable cause) {
        super(message, cause);
        this.httpStatus = httpStatus;
        this.error = error;
    }

    /** HTTP status of the response; {@code 0} when no response was received. */
    public int httpStatus() {
        return httpStatus;
    }

    /** Parsed error body, when the server sent one in the documented format. */
    public Optional<NobitexErrorResponse> error() {
        return Optional.ofNullable(error);
    }
}
