package com.rmrx.marketanalyzer.exchange.crypto.nobitex.model;

/**
 * Error body returned by Nobitex, e.g. {@code {"status":"failed","code":"TooManyRequests","message":"...","backOff":30}}.
 *
 * @param status  usually {@code failed}
 * @param code    machine-readable error code; use this, not {@code message}, for handling
 * @param message human-readable text, for debugging only
 * @param backOff seconds to wait before retrying (rate-limit errors only)
 * @param limit   the limit that was exceeded (rate-limit errors only)
 */
public record NobitexErrorResponse(
        String status,
        String code,
        String message,
        Double backOff,
        String limit) {

    public static final String CODE_TOO_MANY_REQUESTS = "TooManyRequests";

    public boolean isRateLimited() {
        return CODE_TOO_MANY_REQUESTS.equals(code);
    }
}
