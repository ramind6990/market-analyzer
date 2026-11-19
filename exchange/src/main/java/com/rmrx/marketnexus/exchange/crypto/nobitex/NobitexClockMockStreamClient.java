package com.rmrx.marketnexus.exchange.crypto.nobitex;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.rmrx.marketnexus.exchange.client.ExchangeCandleListener;
import com.rmrx.marketnexus.exchange.client.ExchangeStreamClient;
import com.rmrx.marketnexus.exchange.model.ExchangeCandle;
import com.rmrx.marketnexus.exchange.model.ExchangeSymbol;
import com.rmrx.marketnexus.exchange.model.ExchangeTimeframe;
import com.rmrx.marketnexus.exchange.model.ExchangeType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Fake Nobitex candle stream that follows the real clock, like the real WebSocket: each candle is emitted as closed
 * shortly after its period has ended (e.g. the 10:03 M1 at about 10:04:02), one per subscription and period, never in
 * bulk. Open times are aligned to the timeframe; no-trade periods come as Nobitex-style filler candles
 * (o=h=l=c=previous close, volume 0). Prices follow a seeded random walk from a plausible start price per symbol.
 * <p>
 * The first candle emitted per subscription is the one forming at subscription time. One event thread checks once a
 * second; if it was delayed (e.g. machine asleep), the missed candles are emitted in order on the next check.
 */
public final class NobitexClockMockStreamClient implements ExchangeStreamClient {

    private static final Logger log = LoggerFactory.getLogger(NobitexClockMockStreamClient.class);

    private static final Map<String, BigDecimal> START_PRICES = Map.of(
            "BTC/USDT", new BigDecimal("65000"),
            "ETH/USDT", new BigDecimal("1800"),
            "SOL/USDT", new BigDecimal("71"));
    private static final BigDecimal DEFAULT_START_PRICE = new BigDecimal("100");
    private static final double FILLER_PROBABILITY = 0.2;   // share of candles without trades
    private static final double MAX_MOVE = 0.002;            // max relative price move per candle (0.2 %)
    /** How long after a period ends its closed candle is emitted (the real stream also lags a little). */
    private static final Duration CLOSE_DELAY = Duration.ofSeconds(2);
    private static final Duration TICK = Duration.ofSeconds(1);

    private final Clock clock;
    private final Random random;
    private final List<ClockSubscription> subscriptions = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService eventThread = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "nobitex-clock-mock-stream");
        thread.setDaemon(true);
        return thread;
    });
    private volatile boolean connected;

    /** @param seed random seed, for reproducible prices */
    public NobitexClockMockStreamClient(long seed) {
        this(seed, Clock.systemUTC());
    }

    NobitexClockMockStreamClient(long seed, Clock clock) {
        this.random = new Random(seed);
        this.clock = Objects.requireNonNull(clock, "clock");
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
        eventThread.scheduleAtFixedRate(this::tick, 0, TICK.toMillis(), TimeUnit.MILLISECONDS);
        log.info("Nobitex CLOCK MOCK stream connected (closed candles emitted {} after each period ends)",
                CLOSE_DELAY);
    }

    @Override
    public void subscribeCandles(ExchangeSymbol symbol, ExchangeTimeframe timeframe, ExchangeCandleListener listener) {
        Objects.requireNonNull(symbol, "symbol");
        Objects.requireNonNull(timeframe, "timeframe");
        Objects.requireNonNull(listener, "listener");
        NobitexExchangeMapper.toNobitexResolution(timeframe); // same validation as the real stream
        Duration step = timeframe.duration();
        Instant forming = aligned(clock.instant(), step);      // first candle to emit: the one forming now
        BigDecimal startPrice = START_PRICES.getOrDefault(symbol.toString(), DEFAULT_START_PRICE);
        subscriptions.add(new ClockSubscription(symbol, timeframe, listener, step, forming, startPrice));
        log.info("Nobitex CLOCK MOCK stream subscribed {} {}, first candle {}", symbol, timeframe, forming);
    }

    @Override
    public void close() {
        eventThread.shutdownNow();
        connected = false;
        log.info("Nobitex CLOCK MOCK stream closed");
    }

    /** Emits, per subscription, every candle whose period (plus the close delay) has ended. */
    private void tick() {
        Instant now = clock.instant();
        for (ClockSubscription subscription : subscriptions) {
            while (!subscription.nextOpen.plus(subscription.step).plus(CLOSE_DELAY).isAfter(now)) {
                try {
                    ExchangeCandle candle = subscription.next();
                    subscription.listener.onCandleClosed(subscription.symbol, subscription.timeframe, candle);
                } catch (RuntimeException e) {
                    log.warn("Mock candle listener failed for {} {}", subscription.symbol, subscription.timeframe, e);
                }
            }
        }
    }

    /** Start of the period of length {@code step} containing {@code time} (UTC epoch grid). */
    private static Instant aligned(Instant time, Duration step) {
        long stepSeconds = step.toSeconds();
        long seconds = time.getEpochSecond();
        return Instant.ofEpochSecond(seconds - Math.floorMod(seconds, stepSeconds));
    }

    /** State of one subscription: next open time and last close price. Only touched on the event thread. */
    private final class ClockSubscription {
        private final ExchangeSymbol symbol;
        private final ExchangeTimeframe timeframe;
        private final ExchangeCandleListener listener;
        private final Duration step;
        private volatile Instant nextOpen;
        private BigDecimal lastClose;

        ClockSubscription(ExchangeSymbol symbol, ExchangeTimeframe timeframe, ExchangeCandleListener listener,
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
            Instant close = open.plus(step);
            nextOpen = close;
            int scale = Math.max(lastClose.scale(), 2);
            if (random.nextDouble() < FILLER_PROBABILITY) {
                // No trades: Nobitex sends a flat candle at the previous close with zero volume
                return new ExchangeCandle(open, close, lastClose, lastClose, lastClose, lastClose, BigDecimal.ZERO, true);
            }
            BigDecimal openPrice = move(lastClose, scale);
            BigDecimal closePrice = move(openPrice, scale);
            BigDecimal high = openPrice.max(closePrice)
                    .multiply(BigDecimal.valueOf(1 + random.nextDouble() * MAX_MOVE / 2))
                    .setScale(scale, RoundingMode.HALF_UP);
            BigDecimal low = openPrice.min(closePrice)
                    .multiply(BigDecimal.valueOf(1 - random.nextDouble() * MAX_MOVE / 2))
                    .setScale(scale, RoundingMode.HALF_UP);
            BigDecimal volume = BigDecimal.valueOf(random.nextDouble() * 0.05).setScale(6, RoundingMode.HALF_UP);
            lastClose = closePrice;
            return new ExchangeCandle(open, close, openPrice, high, low, closePrice, volume, true);
        }

        private BigDecimal move(BigDecimal price, int scale) {
            double factor = 1 + (random.nextDouble() * 2 - 1) * MAX_MOVE;
            return price.multiply(BigDecimal.valueOf(factor)).setScale(scale, RoundingMode.HALF_UP);
        }
    }
}
