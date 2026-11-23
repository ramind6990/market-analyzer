package com.rmrx.marketanalyzer.exchange.crypto.binance.ws;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;

/**
 * Settings for the Binance market-stream WebSocket, supplied by the caller.
 *
 * @param url            stream root, e.g. {@code wss://stream.binance.com:9443} or
 *                       {@code wss://data-stream.binance.vision}; {@code /stream} (combined streams) is appended when
 *                       missing (required)
 * @param userAgent      sent on the handshake (required)
 * @param connectTimeout handshake timeout (default 10 s)
 */
public record BinanceWebSocketConfig(URI url, String userAgent, Duration connectTimeout) {

    public static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(10);

    public BinanceWebSocketConfig {
        Objects.requireNonNull(url, "url");
        Objects.requireNonNull(userAgent, "userAgent");
        if (userAgent.isBlank()) {
            throw new IllegalArgumentException("userAgent must not be blank");
        }
        connectTimeout = connectTimeout == null ? DEFAULT_CONNECT_TIMEOUT : connectTimeout;
    }

    public BinanceWebSocketConfig(URI url, String userAgent) {
        this(url, userAgent, null);
    }

    /** Endpoint for combined streams subscribed with {@code SUBSCRIBE} messages. */
    public URI combinedStreamUrl() {
        String root = url.toString();
        if (root.endsWith("/")) {
            root = root.substring(0, root.length() - 1);
        }
        return URI.create(root.endsWith("/stream") ? root : root + "/stream");
    }
}
