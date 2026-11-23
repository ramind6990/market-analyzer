package com.rmrx.marketnexus.exchange.crypto.binance;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;

/**
 * Settings for the Binance spot REST API (public market data only), supplied by the caller.
 *
 * @param baseUrl            API root, e.g. {@code https://api.binance.com} or {@code https://data-api.binance.vision}
 *                           (required)
 * @param userAgent          sent on every request (required)
 * @param connectTimeout     TCP/TLS connect timeout (default 10 s)
 * @param requestTimeout     timeout for a whole request/response (default 30 s)
 * @param minRequestInterval minimum spacing between requests from one client (default 250 ms; klines cost weight 2
 *                           of the per-IP weight budget)
 * @param maxRetries         retries after a 429, a 5xx or an I/O error; 0 = no retry (default 3)
 */
public record BinanceConfig(
        URI baseUrl,
        String userAgent,
        Duration connectTimeout,
        Duration requestTimeout,
        Duration minRequestInterval,
        Integer maxRetries) {

    public static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(10);
    public static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(30);
    public static final Duration DEFAULT_MIN_REQUEST_INTERVAL = Duration.ofMillis(250);
    public static final int DEFAULT_MAX_RETRIES = 3;

    /** Optional values left {@code null} are replaced by their defaults. */
    public BinanceConfig {
        Objects.requireNonNull(baseUrl, "baseUrl");
        Objects.requireNonNull(userAgent, "userAgent");
        if (userAgent.isBlank()) {
            throw new IllegalArgumentException("userAgent must not be blank");
        }
        String url = baseUrl.toString();
        if (url.endsWith("/")) {
            baseUrl = URI.create(url.substring(0, url.length() - 1));
        }
        connectTimeout = connectTimeout == null ? DEFAULT_CONNECT_TIMEOUT : connectTimeout;
        requestTimeout = requestTimeout == null ? DEFAULT_REQUEST_TIMEOUT : requestTimeout;
        minRequestInterval = minRequestInterval == null ? DEFAULT_MIN_REQUEST_INTERVAL : minRequestInterval;
        maxRetries = maxRetries == null ? DEFAULT_MAX_RETRIES : maxRetries;
        if (maxRetries < 0) {
            throw new IllegalArgumentException("maxRetries must be >= 0");
        }
    }

    /** Config with the required values only; all others use their defaults. */
    public BinanceConfig(URI baseUrl, String userAgent) {
        this(baseUrl, userAgent, null, null, null, null);
    }
}
