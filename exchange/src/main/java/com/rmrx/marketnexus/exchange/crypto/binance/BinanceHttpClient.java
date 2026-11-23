package com.rmrx.marketnexus.exchange.crypto.binance;

import com.rmrx.marketnexus.exchange.crypto.binance.model.BinanceErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * Low-level HTTP transport of the Binance API clients: base URL, User-Agent, throttling, retries and error translation.
 * <ul>
 *   <li>429 (request rate limit broken): waits {@code Retry-After} seconds (default 60 s) and retries;</li>
 *   <li>418 (IP auto-banned after ignoring 429s): never retried, fails at once;</li>
 *   <li>5xx and I/O errors: exponential backoff (1 s, 2 s, 4 s, ... max 32 s).</li>
 * </ul>
 * Limits are per IP; the used weight is reported in {@code X-MBX-USED-WEIGHT-1M} and logged at debug level.
 */
final class BinanceHttpClient implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(BinanceHttpClient.class);

    private static final Duration DEFAULT_RATE_LIMIT_BACKOFF = Duration.ofSeconds(60);
    private static final Duration RETRY_BASE_DELAY = Duration.ofSeconds(1);
    private static final int MAX_BODY_IN_MESSAGE = 300;

    private final BinanceConfig config;
    private final HttpClient http;
    private final ObjectMapper mapper;

    private final Object throttleLock = new Object();
    private long nextRequestAtNanos = System.nanoTime();

    BinanceHttpClient(BinanceConfig config) {
        this.config = Objects.requireNonNull(config, "config");
        this.http = HttpClient.newBuilder()
                .connectTimeout(config.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        this.mapper = JsonMapper.builder()
                .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
    }

    /**
     * Sends a public GET request and returns the 2xx JSON body as a tree.
     *
     * @param path  path starting with {@code /}, e.g. {@code /api/v3/klines}
     * @param query URL-encoded query string without {@code ?}; may be empty
     */
    JsonNode get(String path, String query) {
        String pathWithQuery = query == null || query.isEmpty() ? path : path + "?" + query;
        int attempt = 0;
        while (true) {
            attempt++;
            throttle();
            HttpResponse<byte[]> response;
            try {
                response = http.send(buildRequest(pathWithQuery), HttpResponse.BodyHandlers.ofByteArray());
            } catch (IOException e) {
                if (attempt <= config.maxRetries()) {
                    Duration delay = retryDelay(attempt);
                    log.warn("Binance GET {} failed ({}), retry {}/{} in {}", path, e.getMessage(), attempt,
                            config.maxRetries(), delay);
                    sleep(delay);
                    continue;
                }
                throw new BinanceApiException(0, null, "Binance GET " + path + " failed: " + e.getMessage(), e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new BinanceApiException(0, null, "Interrupted while calling Binance " + path, e);
            }

            int status = response.statusCode();
            byte[] body = response.body();
            response.headers().firstValue("X-MBX-USED-WEIGHT-1M")
                    .ifPresent(weight -> log.debug("Binance used weight (1m): {}", weight));
            if (status >= 200 && status < 300) {
                return parse(body, status, path);
            }

            BinanceErrorResponse error = parseError(body);
            if (status == 429 && attempt <= config.maxRetries()) {
                Duration backOff = retryAfter(response).orElse(DEFAULT_RATE_LIMIT_BACKOFF);
                log.warn("Binance rate limit on GET {}, retry {}/{} in {}", path, attempt, config.maxRetries(),
                        backOff);
                sleep(backOff);
                continue;
            }
            if (status == 418) {
                log.error("Binance banned this IP (HTTP 418), ban ends in {}",
                        retryAfter(response).map(Duration::toString).orElse("unknown"));
            }
            if (status >= 500 && attempt <= config.maxRetries()) {
                Duration delay = retryDelay(attempt);
                log.warn("Binance GET {} returned HTTP {}, retry {}/{} in {}", path, status, attempt,
                        config.maxRetries(), delay);
                sleep(delay);
                continue;
            }
            throw new BinanceApiException(status, error,
                    "Binance GET " + path + " returned HTTP " + status + ": " + abbreviate(body));
        }
    }

    private HttpRequest buildRequest(String pathWithQuery) {
        return HttpRequest.newBuilder(URI.create(config.baseUrl() + pathWithQuery))
                .timeout(config.requestTimeout())
                .header("User-Agent", config.userAgent())
                .header("Accept", "application/json")
                .GET()
                .build();
    }

    private JsonNode parse(byte[] body, int status, String path) {
        try {
            return mapper.readTree(body);
        } catch (JacksonException e) {
            throw new BinanceApiException(status, null,
                    "Unreadable Binance response from " + path + ": " + abbreviate(body), e);
        }
    }

    private BinanceErrorResponse parseError(byte[] body) {
        if (body == null || body.length == 0) {
            return null;
        }
        try {
            return mapper.readValue(body, BinanceErrorResponse.class);
        } catch (JacksonException e) {
            return null;
        }
    }

    /** {@code Retry-After} in seconds, as sent with 429 and 418. */
    private static Optional<Duration> retryAfter(HttpResponse<?> response) {
        return response.headers().firstValue("Retry-After").flatMap(value -> {
            try {
                long seconds = Long.parseLong(value.strip());
                return seconds > 0 ? Optional.of(Duration.ofSeconds(seconds)) : Optional.empty();
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        });
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

    private static Duration retryDelay(int attempt) {
        return RETRY_BASE_DELAY.multipliedBy(1L << Math.min(attempt - 1, 5));
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BinanceApiException(0, null, "Interrupted while waiting to call Binance", e);
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
