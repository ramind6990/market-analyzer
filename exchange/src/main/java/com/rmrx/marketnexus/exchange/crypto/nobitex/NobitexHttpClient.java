package com.rmrx.marketnexus.exchange.crypto.nobitex;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.rmrx.marketnexus.exchange.crypto.nobitex.model.NobitexErrorResponse;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;

/**
 * Low-level HTTP transport shared by the Nobitex API clients: base URL, User-Agent, optional request signing,
 * throttling, retries on 429 / 5xx / I/O errors, JSON mapping and error translation.
 */
final class NobitexHttpClient implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(NobitexHttpClient.class);

    private static final Duration DEFAULT_RATE_LIMIT_BACKOFF = Duration.ofSeconds(60);
    private static final Duration RETRY_BASE_DELAY = Duration.ofSeconds(1);
    private static final int MAX_BODY_IN_MESSAGE = 300;

    private final NobitexConfig config;
    private final HttpClient http;
    private final ObjectMapper mapper;
    private final NobitexRequestSigner signer;

    private final Object throttleLock = new Object();
    private long nextRequestAtNanos = System.nanoTime();

    NobitexHttpClient(NobitexConfig config) {
        this(config, Clock.systemUTC());
    }

    NobitexHttpClient(NobitexConfig config, Clock clock) {
        this.config = Objects.requireNonNull(config, "config");
        this.http = HttpClient.newBuilder()
                .connectTimeout(config.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        this.mapper = JsonMapper.builder()
                .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
        this.signer = config.findCredentials()
                .map(credentials -> new NobitexRequestSigner(credentials, clock))
                .orElse(null);
    }

    /**
     * Sends a GET request and maps a 2xx JSON body to {@code responseType}.
     *
     * @param path          path starting with {@code /}, e.g. {@code /market/udf/history}
     * @param query         URL-encoded query string without {@code ?}; may be empty
     * @param authenticated whether the endpoint is private and the request must be signed
     */
    <T> T get(String path, String query, Class<T> responseType, boolean authenticated) {
        return send("GET", path, query, new byte[0], responseType, authenticated);
    }

    /** Sends a request with a JSON body (for future private endpoints such as order placement). */
    <T> T sendJson(String method, String path, String query, Object body, Class<T> responseType,
                   boolean authenticated) {
        byte[] rawBody = body == null ? new byte[0] : mapper.writeValueAsBytes(body);
        return send(method, path, query, rawBody, responseType, authenticated);
    }

    private <T> T send(String method, String path, String query, byte[] rawBody, Class<T> responseType,
                       boolean authenticated) {
        String pathWithQuery = query == null || query.isEmpty() ? path : path + "?" + query;
        int attempt = 0;
        while (true) {
            attempt++;
            throttle();
            HttpRequest request = buildRequest(method, pathWithQuery, rawBody, authenticated);
            HttpResponse<byte[]> response;
            try {
                response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
            } catch (IOException e) {
                if (attempt <= config.maxRetries()) {
                    Duration delay = retryDelay(attempt);
                    log.warn("Nobitex {} {} failed ({}), retry {}/{} in {}", method, path, e.getMessage(),
                            attempt, config.maxRetries(), delay);
                    sleep(delay);
                    continue;
                }
                throw new NobitexApiException(0, null, "Nobitex " + method + " " + path + " failed: " + e.getMessage(), e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new NobitexApiException(0, null, "Interrupted while calling Nobitex " + path, e);
            }

            int status = response.statusCode();
            byte[] body = response.body();
            if (status >= 200 && status < 300) {
                return parse(body, responseType, status, path);
            }

            NobitexErrorResponse error = parseError(body);
            if (status == 429 && attempt <= config.maxRetries()) {
                Duration backOff = rateLimitBackOff(error);
                log.warn("Nobitex rate limit on {} {}, retry {}/{} in {}", method, path, attempt,
                        config.maxRetries(), backOff);
                sleep(backOff);
                continue;
            }
            if (status >= 500 && attempt <= config.maxRetries()) {
                Duration delay = retryDelay(attempt);
                log.warn("Nobitex {} {} returned HTTP {}, retry {}/{} in {}", method, path, status, attempt,
                        config.maxRetries(), delay);
                sleep(delay);
                continue;
            }
            throw new NobitexApiException(status, error,
                    "Nobitex " + method + " " + path + " returned HTTP " + status + ": " + abbreviate(body));
        }
    }

    private HttpRequest buildRequest(String method, String pathWithQuery, byte[] rawBody, boolean authenticated) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(config.baseUrl() + pathWithQuery))
                .timeout(config.requestTimeout())
                .header("User-Agent", config.userAgent())
                .header("Accept", "application/json")
                .method(method, rawBody.length == 0
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofByteArray(rawBody));
        if (rawBody.length > 0) {
            builder.header("Content-Type", "application/json");
        }
        if (authenticated) {
            if (signer == null) {
                throw new IllegalStateException("Nobitex credentials are required for " + pathWithQuery
                        + " but none are configured");
            }
            signer.authenticationHeaders(method, pathWithQuery, rawBody).forEach(builder::header);
        }
        return builder.build();
    }

    private <T> T parse(byte[] body, Class<T> type, int status, String path) {
        try {
            return mapper.readValue(body, type);
        } catch (JacksonException e) {
            throw new NobitexApiException(status, null,
                    "Unreadable Nobitex response from " + path + ": " + abbreviate(body), e);
        }
    }

    private NobitexErrorResponse parseError(byte[] body) {
        if (body == null || body.length == 0) {
            return null;
        }
        try {
            return mapper.readValue(body, NobitexErrorResponse.class);
        } catch (JacksonException e) {
            return null;
        }
    }

    /** Spaces requests at least {@code minRequestInterval} apart. */
    private void throttle() {
        long waitNanos;
        synchronized (throttleLock) {
            long now = System.nanoTime();
            long startAt = Math.max(now, nextRequestAtNanos);
            nextRequestAtNanos = startAt + config.minRequestInterval().toNanos();
            waitNanos = startAt - now;
        }
        if (waitNanos > 0) {
            sleep(Duration.ofNanos(waitNanos));
        }
    }

    private static Duration rateLimitBackOff(NobitexErrorResponse error) {
        if (error == null || error.backOff() == null || error.backOff() <= 0) {
            return DEFAULT_RATE_LIMIT_BACKOFF;
        }
        return Duration.ofMillis((long) Math.ceil(error.backOff() * 1000));
    }

    private static Duration retryDelay(int attempt) {
        return RETRY_BASE_DELAY.multipliedBy(1L << Math.min(attempt - 1, 5));
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new NobitexApiException(0, null, "Interrupted while waiting to call Nobitex", e);
        }
    }

    private static String abbreviate(byte[] body) {
        if (body == null || body.length == 0) {
            return "<empty>";
        }
        String text = new String(body, StandardCharsets.UTF_8);
        return text.length() <= MAX_BODY_IN_MESSAGE ? text : text.substring(0, MAX_BODY_IN_MESSAGE) + "...";
    }

    @Override
    public void close() {
        http.close();
    }
}
