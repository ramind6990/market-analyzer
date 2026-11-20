package com.rmrx.marketnexus.marketdata.service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import com.rmrx.marketnexus.exchange.model.ExchangeType;

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
 * @param stream       where candles come from: {@code live} (exchange WebSocket) or {@code mock} (generated); default mock
 * @param mockStream   settings of the mock stream
 * @param worker       threads that store, track and publish candles
 */
@ConfigurationProperties("market-data.ingestion")
public record IngestionProperties(
        ExchangeType exchangeType,
        URI baseUrl,
        URI websocketUrl,
        String userAgent,
        List<String> symbols,
        StreamMode stream,
        MockStream mockStream,
        Worker worker) {

    public IngestionProperties {
        Objects.requireNonNull(exchangeType, "market-data.ingestion.exchange-type");
        Objects.requireNonNull(baseUrl, "market-data.ingestion.base-url");
        Objects.requireNonNull(websocketUrl, "market-data.ingestion.websocket-url");
        Objects.requireNonNull(userAgent, "market-data.ingestion.user-agent");
        symbols = symbols == null ? List.of() : List.copyOf(symbols);
        stream = stream == null ? StreamMode.MOCK : stream;
        mockStream = mockStream == null ? new MockStream(null, null) : mockStream;
        worker = worker == null ? new Worker(null, null) : worker;
    }

    /**
     * LIVE = exchange WebSocket, MOCK = endless generated candles (simulated time, faster than real),
     * MOCK_CLOCK = generated candles following the real clock (one closed candle per period, like the WebSocket),
     * MOCK_GAPS = one-off history with gaps (reconciliation tests).
     */
    public enum StreamMode { LIVE, MOCK, MOCK_CLOCK, MOCK_GAPS }

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
     * Worker threads ({@code market-data.ingestion.worker.*}). Each symbol is always handled by the same thread, so the
     * candles of one series are stored and tracked in arrival order; different symbols run in parallel.
     *
     * @param threads       worker threads, each with its own queue (default 3)
     * @param queueCapacity candles waiting to be stored/published, split evenly over the threads; when a thread's
     *                      queue is full, new candles for its symbols are dropped and logged (default 10000)
     */
    public record Worker(Integer threads, Integer queueCapacity) {

        public Worker {
            threads = threads == null ? 3 : threads;
            queueCapacity = queueCapacity == null ? 10_000 : queueCapacity;
            if (threads < 1 || queueCapacity < threads) {
                throw new IllegalArgumentException("market-data.ingestion.worker: need threads >= 1 and "
                        + "queue-capacity >= threads");
            }
        }

        /** Queue size of one thread. */
        public int queueCapacityPerThread() {
            return queueCapacity / threads;
        }
    }
}
