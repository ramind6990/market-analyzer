package com.rmrx.marketanalyzer.exchange.crypto.nobitex;

import java.util.Objects;

/**
 * Nobitex API key credentials, needed only for private endpoints.
 *
 * @param apiKey     public key shown in the Nobitex panel, sent as the {@code Nobitex-Key} header
 * @param privateKey Ed25519 private key (32-byte seed) as URL-safe Base64; used locally for signing, never sent
 */
public record NobitexCredentials(String apiKey, String privateKey) {

    public NobitexCredentials {
        requireNotBlank(apiKey, "apiKey");
        requireNotBlank(privateKey, "privateKey");
        apiKey = apiKey.strip();
        privateKey = privateKey.strip();
    }

    @Override
    public String toString() {
        return "NobitexCredentials[apiKey=" + mask(apiKey) + ", privateKey=****]";
    }

    private static String mask(String value) {
        return value.length() <= 4 ? "****" : value.substring(0, 4) + "****";
    }

    private static void requireNotBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
