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
import com.rmrx.marketnexus.marketdata.service.tracking.tracker.SeriesKey;
import com.rmrx.marketnexus.marketdata.service.tracking.tracker.SeriesTracker;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

/**
 * Receives closed M1 candles from the exchange stream, stores them (idempotent upsert) and publishes them to Kafka
 * ({@code market-data.kafka.topics.m1-live}, input of the Flink candle aggregator).
 * <p>
 * Stream callbacks only enqueue; worker threads ({@code market-data.ingestion.worker.*}) do the database, tracking and
 * Kafka work, so a slow or unavailable database never blocks the WebSocket thread. Each symbol is always handled by
 * the same thread, so the candles of one market are stored, tracked and published in arrival order.
 * <p>
 * Per candle: store → hand to {@link SeriesTracker} (after the commit, also when unchanged: the book ignores
 * duplicates) → publish, only if it was new or changed.
 */
@Service
public class M1IngestionService implements ExchangeCandleListener, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(M1IngestionService.class);

    private final CandleRepository repository;
    private final CandlePublisher publisher;
    private final String topic;
    private final SeriesTracker tracker;
    private final ExchangeType exchangeType;
    /** One single-thread executor per stripe; a symbol always maps to the same stripe. */
    private final List<ExecutorService> workers;

    public M1IngestionService(CandleRepository repository, CandlePublisher publisher, SeriesTracker tracker,
                              IngestionProperties properties, CandleKafkaProperties kafka) {
        this.repository = repository;
        this.publisher = publisher;
        this.tracker = tracker;
        this.topic = kafka.topics().m1Live();
        this.exchangeType = properties.exchangeType();
        IngestionProperties.Worker pool = properties.worker();
        AtomicInteger threadNumber = new AtomicInteger();
        this.workers = IntStream.range(0, pool.threads())
                .<ExecutorService>mapToObj(i -> new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                        new ArrayBlockingQueue<>(pool.queueCapacityPerThread()),
                        // Non-daemon threads; shut down (and drained) in close() when Spring stops the app
                        runnable -> new Thread(runnable, "m1-ingestion-" + threadNumber.incrementAndGet())))
                .toList();
    }

    @Override
    public void onCandleClosed(ExchangeSymbol symbol, ExchangeTimeframe timeframe, ExchangeCandle exchangeCandle) {
        if (timeframe != ExchangeTimeframe.M1) {
            log.warn("Ignoring {} candle for {}: this service ingests M1 only", timeframe, symbol);
            return;
        }
        Candle candle = toCandle(symbol, exchangeCandle);
        try {
            workerFor(candle.symbol()).execute(() -> ingest(candle));
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
        tracker.onClosedCandle(new SeriesKey(candle.exchange(), candle.symbol(), candle.timeframe()), candle.openTime());
        if (changed) {
            publisher.publish(topic, candle);
        } else {
            log.debug("Unchanged {} {} {}, not republished", candle.symbol(), candle.timeframe(), candle.openTime());
        }
    }

    private ExecutorService workerFor(String symbol) {
        return workers.get(Math.floorMod(symbol.hashCode(), workers.size()));
    }

    private Candle toCandle(ExchangeSymbol symbol, ExchangeCandle candle) {
        return new Candle(exchangeType.name(), symbol.toString(), Timeframe.M1, candle.openTime(),
                candle.open(), candle.high(), candle.low(), candle.close(), candle.volume());
    }

    @Override
    public void close() throws InterruptedException {
        workers.forEach(ExecutorService::shutdown);
        for (ExecutorService worker : workers) {
            if (!worker.awaitTermination(10, TimeUnit.SECONDS)) {
                int pending = worker.shutdownNow().size();
                log.warn("Ingestion worker did not finish within 10 s; {} queued candles dropped", pending);
            }
        }
    }
}
