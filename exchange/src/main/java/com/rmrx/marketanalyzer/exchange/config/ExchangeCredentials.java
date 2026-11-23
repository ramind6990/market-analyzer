package com.rmrx.marketanalyzer.exchange.config;

import java.util.Objects;

/**
 * API credentials for an exchange's private endpoints.
 *
 * @param apiKey    public API key
 * @param secretKey secret used to sign requests (for Nobitex: the Ed25519 private key as URL-safe Base64)
 */
public record ExchangeCredentials(String apiKey, String secretKey) {

    public ExchangeCredentials {
        Objects.requireNonNull(apiKey, "apiKey");
        Objects.requireNonNull(secretKey, "secretKey");
        if (apiKey.isBlank() || secretKey.isBlank()) {
            throw new IllegalArgumentException("apiKey and secretKey must not be blank");
        }
        apiKey = apiKey.strip();
        secretKey = secretKey.strip();
    }

    @Override
    public String toString() {
        String maskedKey = apiKey.length() <= 4 ? "****" : apiKey.substring(0, 4) + "****";
        return "ExchangeCredentials[apiKey=" + maskedKey + ", secretKey=****]";
    }
}
