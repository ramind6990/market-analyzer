package com.rmrx.marketanalyzer.marketdata.service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import com.rmrx.marketanalyzer.exchange.model.ExchangeType;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * Settings of the ingestion app ({@code market-data.ingestion.*} in application.yml).
 *
 * @param exchangeType exchange to stream from, e.g. {@code CRYPTO_NOBITEX}
 * @param baseUrl      exchange REST API root
 * @param websocketUrl exchange WebSocket endpoint
 * @param userAgent    User-Agent sent to the exchange
 * @param symbols      markets as {@code BASE/QUOTE}, e.g. {@code BTC/USDT}
 * @param topic        Kafka topic for live M1 candles
 * @param stream       where candles come from: {@code live} (exchange WebSocket) or {@code mock} (generated); default mock
 * @param mockStream   settings of the mock stream
 * @param publisher    where stored candles are published: {@code kafka} or {@code logging} (log only); default logging
 * @param worker       thread pool that stores and publishes candles
 */
@ConfigurationProperties("market-data.ingestion")
public record IngestionProperties(
        ExchangeType exchangeType,
        URI baseUrl,
        URI websocketUrl,
        String userAgent,
        List<String> symbols,
        String topic,
        StreamMode stream,
        MockStream mockStream,
        PublisherType publisher,
        Worker worker) {

    public IngestionProperties {
        Objects.requireNonNull(exchangeType, "market-data.ingestion.exchange-type");
        Objects.requireNonNull(baseUrl, "market-data.ingestion.base-url");
        Objects.requireNonNull(websocketUrl, "market-data.ingestion.websocket-url");
        Objects.requireNonNull(userAgent, "market-data.ingestion.user-agent");
        Objects.requireNonNull(topic, "market-data.ingestion.topic");
        symbols = symbols == null ? List.of() : List.copyOf(symbols);
        stream = stream == null ? StreamMode.MOCK : stream;
        mockStream = mockStream == null ? new MockStream(null, null) : mockStream;
        publisher = publisher == null ? PublisherType.LOGGING : publisher;
        worker = worker == null ? new Worker(null, null, null, null) : worker;
    }

    public enum StreamMode { LIVE, MOCK }

    public enum PublisherType { KAFKA, LOGGING }

    /**
     * Mock stream ({@code market-data.ingestion.mock-stream.*}).
     *
     * @param emitInterval real time between two candles of one symbol (default 1 s, i.e. one simulated minute per second)
     * @param seed         random seed for reproducible data (default 42)
     */
    public record MockStream(Duration emitInterval, Long seed) {

        public MockStream {
            emitInterval = emitInterval == null ? Duration.ofSeconds(1) : emitInterval;
            seed = seed == null ? 42L : seed;
        }
    }

    /**
     * Worker pool ({@code market-data.ingestion.worker.*}). Like any {@code ThreadPoolExecutor}, it runs
     * {@code minPoolSize} threads and only adds threads (up to {@code maxPoolSize}) once the queue is full.
     *
     * @param minPoolSize   threads always kept (default 1)
     * @param maxPoolSize   upper bound of threads (default 5)
     * @param queueCapacity candles waiting to be stored/published; when the queue is full and all threads are busy,
     *                      new candles are dropped and logged (default 10000)
     * @param keepAlive     how long threads above {@code minPoolSize} stay idle before ending (default 60 s)
     */
    public record Worker(Integer minPoolSize, Integer maxPoolSize, Integer queueCapacity, Duration keepAlive) {

        public Worker {
            minPoolSize = minPoolSize == null ? 1 : minPoolSize;
            maxPoolSize = maxPoolSize == null ? 5 : maxPoolSize;
            queueCapacity = queueCapacity == null ? 10_000 : queueCapacity;
            keepAlive = keepAlive == null ? Duration.ofSeconds(60) : keepAlive;
            if (minPoolSize < 1 || maxPoolSize < minPoolSize || queueCapacity < 1) {
                throw new IllegalArgumentException("market-data.ingestion.worker: need 1 <= min-pool-size <= "
                        + "max-pool-size and queue-capacity >= 1");
            }
        }
    }
}
