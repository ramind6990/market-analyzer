package com.rmrx.marketnexus.marketdata.service.ingestion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import com.rmrx.marketnexus.exchange.client.ExchangeCandleListener;
import com.rmrx.marketnexus.exchange.model.ExchangeCandle;
import com.rmrx.marketnexus.exchange.model.ExchangeSymbol;
import com.rmrx.marketnexus.exchange.model.ExchangeTimeframe;
import com.rmrx.marketnexus.exchange.model.ExchangeType;
import com.rmrx.marketnexus.marketdata.service.candle.CandleRepository;
import com.rmrx.marketnexus.marketdata.service.config.CandleKafkaProperties;
import com.rmrx.marketnexus.marketdata.service.config.IngestionProperties;
import com.rmrx.marketnexus.marketdata.service.publish.CandlePublisher;
import com.rmrx.marketnexus.marketdata.model.Candle;
import com.rmrx.marketnexus.marketdata.model.Timeframe;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Receives closed M1 candles from the exchange stream, stores them (idempotent upsert) and publishes them to Kafka
 * ({@code market-data.kafka.topics.m1-live}, input of the Flink candle aggregator).
 * <p>
 * Stream callbacks only enqueue; a worker pool ({@code market-data.ingestion.worker.*}) does the database and Kafka
 * work, so a slow or unavailable database never blocks the WebSocket thread. A candle is published only after it was
 * stored and only if it was new or changed. With more than one thread, publish order across candles is not
 * guaranteed (live M1 per market arrive a minute apart, so in practice they don't overlap).
 */
@Service
public class M1IngestionService implements ExchangeCandleListener, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(M1IngestionService.class);

    private final CandleRepository repository;
    private final CandlePublisher publisher;
    private final String topic;
    private final ExchangeType exchangeType;
    private final ExecutorService worker;

    public M1IngestionService(CandleRepository repository, CandlePublisher publisher, IngestionProperties properties,
                              CandleKafkaProperties kafka) {
        this.repository = repository;
        this.publisher = publisher;
        this.topic = kafka.topics().m1Live();
        this.exchangeType = properties.exchangeType();
        IngestionProperties.Worker pool = properties.worker();
        AtomicInteger threadNumber = new AtomicInteger();
        this.worker = new ThreadPoolExecutor(pool.minPoolSize(), pool.maxPoolSize(),
                pool.keepAlive().toMillis(), TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(pool.queueCapacity()),
                // Non-daemon threads; the pool is shut down (and drained) in close() when Spring stops the app
                runnable -> new Thread(runnable, "m1-ingestion-" + threadNumber.incrementAndGet()));
    }

    @Override
    public void onCandleClosed(ExchangeSymbol symbol, ExchangeTimeframe timeframe, ExchangeCandle exchangeCandle) {
        if (timeframe != ExchangeTimeframe.M1) {
            log.warn("Ignoring {} candle for {}: this service ingests M1 only", timeframe, symbol);
            return;
        }
        Candle candle = toCandle(symbol, exchangeCandle);
        try {
            worker.execute(() -> ingest(candle));
        } catch (RejectedExecutionException e) {
            // Queue full (database down for long): drop; the reconciler will fill the gap later.
            log.error("Ingestion queue full, dropping {} {} {}", candle.symbol(), candle.timeframe(), candle.openTime());
        }
    }

    void ingest(Candle candle) {
        if (!candle.isConsistent()) {
            log.warn("Rejecting inconsistent candle {}", candle);
            return;
        }
        boolean changed;
        try {
            changed = repository.upsert(candle);
        } catch (RuntimeException e) {
            // Not published: downstream only ever sees stored candles. The gap is repaired by reconciliation.
            log.error("Failed to store {} {} {}", candle.symbol(), candle.timeframe(), candle.openTime(), e);
            return;
        }
        if (changed) {
            publisher.publish(topic, candle);
        } else {
            log.debug("Unchanged {} {} {}, not republished", candle.symbol(), candle.timeframe(), candle.openTime());
        }
    }

    private Candle toCandle(ExchangeSymbol symbol, ExchangeCandle candle) {
        return new Candle(exchangeType.name(), symbol.toString(), Timeframe.M1, candle.openTime(),
                candle.open(), candle.high(), candle.low(), candle.close(), candle.volume());
    }

    @Override
    public void close() throws InterruptedException {
        worker.shutdown();
        if (!worker.awaitTermination(10, TimeUnit.SECONDS)) {
            int pending = worker.shutdownNow().size();
            log.warn("Ingestion worker did not finish within 10 s; {} queued candles dropped", pending);
        }
    }
}
