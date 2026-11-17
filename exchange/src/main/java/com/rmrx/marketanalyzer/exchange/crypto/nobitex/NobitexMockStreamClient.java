package com.rmrx.marketanalyzer.exchange.crypto.nobitex;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.rmrx.marketanalyzer.exchange.client.ExchangeCandleListener;
import com.rmrx.marketanalyzer.exchange.client.ExchangeStreamClient;
import com.rmrx.marketanalyzer.exchange.model.ExchangeCandle;
import com.rmrx.marketanalyzer.exchange.model.ExchangeSymbol;
import com.rmrx.marketanalyzer.exchange.model.ExchangeTimeframe;
import com.rmrx.marketanalyzer.exchange.model.ExchangeType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Fake Nobitex candle stream for running without the real WebSocket. Behaves like the real stream where it matters:
 * one event thread, closed candles per subscription in order, aligned open times, and Nobitex-style filler candles
 * (o=h=l=c=previous close, volume 0) for "minutes without trades".
 * <p>
 * Simulated time: every {@code emitInterval} each subscription emits its next closed candle, so one minute of market
 * time can pass in e.g. one second. Prices follow a random walk from a plausible start price per symbol.
 */
public final class NobitexMockStreamClient implements ExchangeStreamClient {

    private static final Logger log = LoggerFactory.getLogger(NobitexMockStreamClient.class);

    private static final Map<String, BigDecimal> START_PRICES = Map.of(
            "BTC/USDT", new BigDecimal("65000"),
            "ETH/USDT", new BigDecimal("1800"),
            "SOL/USDT", new BigDecimal("71"));
    private static final BigDecimal DEFAULT_START_PRICE = new BigDecimal("100");
    private static final double FILLER_PROBABILITY = 0.2;   // share of candles without trades
    private static final double MAX_MOVE = 0.002;            // max relative price move per candle (0.2 %)

    private final Duration emitInterval;
    private final Random random;
    private final List<MockSubscription> subscriptions = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService eventThread = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "nobitex-mock-stream");
        thread.setDaemon(true);
        return thread;
    });
    private volatile boolean connected;

    /**
     * @param emitInterval real time between two candles of the same subscription
     * @param seed         random seed, for reproducible data
     */
    public NobitexMockStreamClient(Duration emitInterval, long seed) {
        this.emitInterval = Objects.requireNonNull(emitInterval, "emitInterval");
        if (emitInterval.isZero() || emitInterval.isNegative()) {
            throw new IllegalArgumentException("emitInterval must be positive");
        }
        this.random = new Random(seed);
    }

    @Override
    public ExchangeType exchangeType() {
        return ExchangeType.CRYPTO_NOBITEX;
    }

    @Override
    public synchronized void connect() {
        if (connected) {
            return;
        }
        connected = true;
        eventThread.scheduleAtFixedRate(this::emitAll, 0, emitInterval.toMillis(), TimeUnit.MILLISECONDS);
        log.info("Nobitex MOCK stream connected (one candle per subscription every {})", emitInterval);
    }

    @Override
    public void subscribeCandles(ExchangeSymbol symbol, ExchangeTimeframe timeframe, ExchangeCandleListener listener) {
        Objects.requireNonNull(symbol, "symbol");
        Objects.requireNonNull(timeframe, "timeframe");
        Objects.requireNonNull(listener, "listener");
        NobitexExchangeMapper.toNobitexResolution(timeframe); // same validation as the real stream
        Duration step = timeframe.duration();
        Instant firstOpen = alignedNow(step).minus(step);      // the most recently closed candle
        BigDecimal startPrice = START_PRICES.getOrDefault(symbol.toString(), DEFAULT_START_PRICE);
        subscriptions.add(new MockSubscription(symbol, timeframe, listener, step, firstOpen, startPrice));
        log.info("Nobitex MOCK stream subscribed {} {}", symbol, timeframe);
    }

    @Override
    public void close() {
        eventThread.shutdownNow();
        connected = false;
        log.info("Nobitex MOCK stream closed");
    }

    private void emitAll() {
        for (MockSubscription subscription : subscriptions) {
            try {
                ExchangeCandle candle = subscription.next();
                subscription.listener.onCandleClosed(subscription.symbol, subscription.timeframe, candle);
            } catch (RuntimeException e) {
                log.warn("Mock candle listener failed for {} {}", subscription.symbol, subscription.timeframe, e);
            }
        }
    }

    private static Instant alignedNow(Duration step) {
        long stepSeconds = step.toSeconds();
        long now = Instant.now().truncatedTo(ChronoUnit.SECONDS).getEpochSecond();
        return Instant.ofEpochSecond(now - Math.floorMod(now, stepSeconds));
    }

    /** State of one subscription: next open time and last close price. Only touched on the event thread. */
    private final class MockSubscription {
        private final ExchangeSymbol symbol;
        private final ExchangeTimeframe timeframe;
        private final ExchangeCandleListener listener;
        private final Duration step;
        private Instant nextOpen;
        private BigDecimal lastClose;

        MockSubscription(ExchangeSymbol symbol, ExchangeTimeframe timeframe, ExchangeCandleListener listener,
                         Duration step, Instant firstOpen, BigDecimal startPrice) {
            this.symbol = symbol;
            this.timeframe = timeframe;
            this.listener = listener;
            this.step = step;
            this.nextOpen = firstOpen;
            this.lastClose = startPrice;
        }

        ExchangeCandle next() {
            Instant open = nextOpen;
            nextOpen = open.plus(step);
            int scale = Math.max(lastClose.scale(), 2);
            ExchangeCandle candle;
            if (random.nextDouble() < FILLER_PROBABILITY) {
                // No trades: Nobitex sends a flat candle at the previous close with zero volume
                candle = new ExchangeCandle(open, nextOpen, lastClose, lastClose, lastClose, lastClose,
                        BigDecimal.ZERO, true);
            } else {
                BigDecimal openPrice = move(lastClose, scale);
                BigDecimal closePrice = move(openPrice, scale);
                BigDecimal high = openPrice.max(closePrice).multiply(BigDecimal.valueOf(1 + random.nextDouble() * MAX_MOVE / 2))
                        .setScale(scale, RoundingMode.HALF_UP);
                BigDecimal low = openPrice.min(closePrice).multiply(BigDecimal.valueOf(1 - random.nextDouble() * MAX_MOVE / 2))
                        .setScale(scale, RoundingMode.HALF_UP);
                BigDecimal volume = BigDecimal.valueOf(random.nextDouble() * 0.05).setScale(6, RoundingMode.HALF_UP);
                candle = new ExchangeCandle(open, nextOpen, openPrice, high, low, closePrice, volume, true);
                lastClose = closePrice;
            }
            return candle;
        }

        private BigDecimal move(BigDecimal price, int scale) {
            double factor = 1 + (random.nextDouble() * 2 - 1) * MAX_MOVE;
            return price.multiply(BigDecimal.valueOf(factor)).setScale(scale, RoundingMode.HALF_UP);
        }
    }
}
