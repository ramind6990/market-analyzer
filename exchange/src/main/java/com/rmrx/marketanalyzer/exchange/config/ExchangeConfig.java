package com.rmrx.marketanalyzer.exchange.config;

import com.rmrx.marketanalyzer.exchange.model.ExchangeType;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * Connection settings for one exchange, supplied by the caller.
 * Required: {@code exchangeType}, {@code baseUrl}, {@code userAgent}. Optional values may be {@code null},
 * in which case the exchange implementation applies its own default.
 *
 * @param exchangeType       which exchange to talk to (required)
 * @param baseUrl            REST API root (required)
 * @param userAgent          User-Agent header sent on every request (required)
 * @param websocketUrl       WebSocket endpoint (optional; required by {@code ExchangeClients.createStream})
 * @param connectTimeout     connect timeout (optional)
 * @param requestTimeout     timeout for a whole request/response (optional)
 * @param minRequestInterval minimum spacing between requests, to respect rate limits (optional)
 * @param maxRetries         retries after rate-limit, server or I/O errors (optional)
 * @param credentials        API credentials for private endpoints (optional; public endpoints only when absent)
 */
public record ExchangeConfig(
        ExchangeType exchangeType,
        URI baseUrl,
        String userAgent,
        URI websocketUrl,
        Duration connectTimeout,
        Duration requestTimeout,
        Duration minRequestInterval,
        Integer maxRetries,
        ExchangeCredentials credentials) {

    public ExchangeConfig {
        Objects.requireNonNull(exchangeType, "exchangeType");
        Objects.requireNonNull(baseUrl, "baseUrl");
        Objects.requireNonNull(userAgent, "userAgent");
        if (userAgent.isBlank()) {
            throw new IllegalArgumentException("userAgent must not be blank");
        }
        if (maxRetries != null && maxRetries < 0) {
            throw new IllegalArgumentException("maxRetries must be >= 0");
        }
    }

    /** Config with the required values only. */
    public ExchangeConfig(ExchangeType exchangeType, URI baseUrl, String userAgent) {
        this(exchangeType, baseUrl, userAgent, null, null, null, null, null, null);
    }

    public Optional<ExchangeCredentials> findCredentials() {
        return Optional.ofNullable(credentials);
    }

    public ExchangeConfig withWebsocketUrl(URI newWebsocketUrl) {
        return new ExchangeConfig(exchangeType, baseUrl, userAgent, newWebsocketUrl, connectTimeout,
                requestTimeout, minRequestInterval, maxRetries, credentials);
    }

    public ExchangeConfig withTimeouts(Duration newConnectTimeout, Duration newRequestTimeout) {
        return new ExchangeConfig(exchangeType, baseUrl, userAgent, websocketUrl, newConnectTimeout,
                newRequestTimeout, minRequestInterval, maxRetries, credentials);
    }

    public ExchangeConfig withMinRequestInterval(Duration newMinRequestInterval) {
        return new ExchangeConfig(exchangeType, baseUrl, userAgent, websocketUrl, connectTimeout,
                requestTimeout, newMinRequestInterval, maxRetries, credentials);
    }

    public ExchangeConfig withMaxRetries(Integer newMaxRetries) {
        return new ExchangeConfig(exchangeType, baseUrl, userAgent, websocketUrl, connectTimeout,
                requestTimeout, minRequestInterval, newMaxRetries, credentials);
    }

    public ExchangeConfig withCredentials(ExchangeCredentials newCredentials) {
        return new ExchangeConfig(exchangeType, baseUrl, userAgent, websocketUrl, connectTimeout,
                requestTimeout, minRequestInterval, maxRetries, newCredentials);
    }
}
