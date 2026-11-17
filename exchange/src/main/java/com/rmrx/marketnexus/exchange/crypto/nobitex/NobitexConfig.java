package com.rmrx.marketnexus.exchange.crypto.nobitex;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * Settings for talking to the Nobitex REST API, supplied by the caller (e.g. a Spring Boot module).
 * Only {@code baseUrl} and {@code userAgent} are required; everything else has a default.
 *
 * @param baseUrl            API root, e.g. {@code https://apiv2.nobitex.ir} (required)
 * @param userAgent          sent on every request; Nobitex asks bots to use {@code TraderBot/<name-and-version>} (required)
 * @param connectTimeout     TCP/TLS connect timeout (default 10 s)
 * @param requestTimeout     timeout for a whole request/response (default 30 s)
 * @param minRequestInterval minimum spacing between requests from one client (default 1.1 s; OHLC allows 60/minute)
 * @param maxRetries         retries after a 429, a 5xx or an I/O error; 0 = no retry (default 3)
 * @param credentials        API key credentials for private endpoints; {@code null} for public-only use
 */
public record NobitexConfig(
        URI baseUrl,
        String userAgent,
        Duration connectTimeout,
        Duration requestTimeout,
        Duration minRequestInterval,
        Integer maxRetries,
        NobitexCredentials credentials) {

    public static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(10);
    public static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(30);
    public static final Duration DEFAULT_MIN_REQUEST_INTERVAL = Duration.ofMillis(1100);
    public static final int DEFAULT_MAX_RETRIES = 3;

    /** Optional values left {@code null} are replaced by their defaults. */
    public NobitexConfig {
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
    public NobitexConfig(URI baseUrl, String userAgent) {
        this(baseUrl, userAgent, null, null, null, null, null);
    }

    public Optional<NobitexCredentials> findCredentials() {
        return Optional.ofNullable(credentials);
    }

    public NobitexConfig withCredentials(NobitexCredentials newCredentials) {
        return new NobitexConfig(baseUrl, userAgent, connectTimeout, requestTimeout,
                minRequestInterval, maxRetries, newCredentials);
    }

    public NobitexConfig withTimeouts(Duration newConnectTimeout, Duration newRequestTimeout) {
        return new NobitexConfig(baseUrl, userAgent, newConnectTimeout, newRequestTimeout,
                minRequestInterval, maxRetries, credentials);
    }

    public NobitexConfig withMinRequestInterval(Duration newMinRequestInterval) {
        return new NobitexConfig(baseUrl, userAgent, connectTimeout, requestTimeout,
                newMinRequestInterval, maxRetries, credentials);
    }

    public NobitexConfig withMaxRetries(int newMaxRetries) {
        return new NobitexConfig(baseUrl, userAgent, connectTimeout, requestTimeout,
                minRequestInterval, newMaxRetries, credentials);
    }
}
