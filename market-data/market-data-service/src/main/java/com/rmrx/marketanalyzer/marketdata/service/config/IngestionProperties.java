package com.rmrx.marketanalyzer.marketdata.service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import com.rmrx.marketanalyzer.exchange.model.ExchangeType;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Settings of the ingestion app ({@code market-data.ingestion.*} in application.yml).
 *
 * @param exchangeType exchange to stream from: {@code crypto_nobitex} or {@code crypto_binance} (case-insensitive)
 * @param exchanges    connection settings per exchange; the entry of {@code exchangeType} is used
 * @param userAgent    User-Agent sent to the exchange
 * @param symbols      markets as {@code BASE/QUOTE}, e.g. {@code BTC/USDT}
 * @param worker       threads that store, track and publish candles
 */
@ConfigurationProperties("market-data.ingestion")
public record IngestionProperties(
        ExchangeType exchangeType,
        Map<ExchangeType, Endpoints> exchanges,
        String userAgent,
        List<String> symbols,
        Worker worker) {

    public IngestionProperties {
        Objects.requireNonNull(exchangeType, "market-data.ingestion.exchange-type");
        exchanges = exchanges == null ? Map.of() : Map.copyOf(exchanges);
        if (!exchanges.containsKey(exchangeType)) {
            throw new IllegalArgumentException("market-data.ingestion.exchanges has no entry for " + exchangeType);
        }
        Objects.requireNonNull(userAgent, "market-data.ingestion.user-agent");
        symbols = symbols == null ? List.of() : List.copyOf(symbols);
        worker = worker == null ? new Worker(null, null) : worker;
    }

    /** REST API root of the configured exchange. */
    public URI baseUrl() {
        return exchanges.get(exchangeType).baseUrl();
    }

    /** WebSocket endpoint of the configured exchange. */
    public URI websocketUrl() {
        return exchanges.get(exchangeType).websocketUrl();
    }

    /**
     * Endpoints of one exchange ({@code market-data.ingestion.exchanges.<type>.*}).
     *
     * @param baseUrl      exchange REST API root
     * @param websocketUrl exchange WebSocket endpoint
     */
    public record Endpoints(URI baseUrl, URI websocketUrl) {

        public Endpoints {
            Objects.requireNonNull(baseUrl, "base-url");
            Objects.requireNonNull(websocketUrl, "websocket-url");
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
