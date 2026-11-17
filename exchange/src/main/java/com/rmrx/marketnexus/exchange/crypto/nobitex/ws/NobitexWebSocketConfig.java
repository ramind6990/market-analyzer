package com.rmrx.marketnexus.exchange.crypto.nobitex.ws;

import java.net.URI;
import java.util.Objects;
import java.util.Optional;

/**
 * Settings for the Nobitex WebSocket (Centrifugo) connection, supplied by the caller.
 *
 * @param url             WebSocket endpoint, e.g. {@code wss://ws.nobitex.ir/connection/websocket} (required)
 * @param userAgent       sent on the handshake; Nobitex asks bots to use {@code TraderBot/<name-and-version>} (required)
 * @param connectionToken JWT for private channels, from {@code GET /auth/ws/token/} (valid 1200 s);
 *                        {@code null} for public channels such as candles
 */
public record NobitexWebSocketConfig(URI url, String userAgent, String connectionToken) {

    public NobitexWebSocketConfig {
        Objects.requireNonNull(url, "url");
        Objects.requireNonNull(userAgent, "userAgent");
        if (userAgent.isBlank()) {
            throw new IllegalArgumentException("userAgent must not be blank");
        }
        if (connectionToken != null && connectionToken.isBlank()) {
            connectionToken = null;
        }
    }

    /** Config for public channels only. */
    public NobitexWebSocketConfig(URI url, String userAgent) {
        this(url, userAgent, null);
    }

    public Optional<String> findConnectionToken() {
        return Optional.ofNullable(connectionToken);
    }

    @Override
    public String toString() {
        return "NobitexWebSocketConfig[url=" + url + ", userAgent=" + userAgent
                + ", connectionToken=" + (connectionToken == null ? "none" : "****") + "]";
    }
}
