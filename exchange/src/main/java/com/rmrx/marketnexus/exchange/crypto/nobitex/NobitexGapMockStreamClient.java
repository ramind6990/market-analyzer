package com.rmrx.marketnexus.exchange.crypto.nobitex;

import com.rmrx.marketnexus.exchange.client.ExchangeCandleListener;
import com.rmrx.marketnexus.exchange.client.ExchangeStreamClient;
import com.rmrx.marketnexus.exchange.model.ExchangeCandle;
import com.rmrx.marketnexus.exchange.model.ExchangeSymbol;
import com.rmrx.marketnexus.exchange.model.ExchangeTimeframe;
import com.rmrx.marketnexus.exchange.model.ExchangeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Fake Nobitex stream for testing reconciliation: on {@link #connect()} it emits, once, a fixed history of closed
 * candles per subscription that contains gaps, then stays silent.
 * <p>
 * Per subscription: {@code candleCount} candles starting {@code startAgo} before now (aligned to the timeframe), with
 * {@code minGaps..maxGaps} gaps at random positions, each missing {@code minGapCandles..maxGapCandles} candles.
 * The generated gaps are logged, so the result of gap detection can be compared with them.
 */
public final class NobitexGapMockStreamClient implements ExchangeStreamClient {

    private static final Logger log = LoggerFactory.getLogger(NobitexGapMockStreamClient.class);

    private static final Map<String, BigDecimal> START_PRICES = Map.of(
            "BTC/USDT", new BigDecimal("65000"),
            "ETH/USDT", new BigDecimal("1800"),
            "SOL/USDT", new BigDecimal("71"));
    private static final BigDecimal DEFAULT_START_PRICE = new BigDecimal("100");
    private static final double MAX_MOVE = 0.002;

    private final int candleCount;
    private final Duration startAgo;
    private final int minGaps;
    private final int maxGaps;
    private final int minGapCandles;
    private final int maxGapCandles;
    private final Random random;
    private final List<Subscription> subscriptions = new CopyOnWriteArrayList<>();
    private final ExecutorService eventThread = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "nobitex-gap-mock-stream");
        thread.setDaemon(true);
        return thread;
    });
    private boolean connected;

    public NobitexGapMockStreamClient(int candleCount, Duration startAgo, int minGaps, int maxGaps,
                                      int minGapCandles, int maxGapCandles, long seed) {
        if (candleCount < 2 || minGaps < 0 || maxGaps < minGaps || maxGaps >= candleCount
                || minGapCandles < 1 || maxGapCandles < minGapCandles) {
            throw new IllegalArgumentException("Invalid gap mock settings");
        }
        this.candleCount = candleCount;
        this.startAgo = Objects.requireNonNull(startAgo, "startAgo");
        this.minGaps = minGaps;
        this.maxGaps = maxGaps;
        this.minGapCandles = minGapCandles;
        this.maxGapCandles = maxGapCandles;
        this.random = new Random(seed);
    }

    /** Defaults for the reconciliation test: 100 candles from 8 hours ago, 3–4 gaps of 6–15 missing candles each. */
    public NobitexGapMockStreamClient(long seed) {
        this(100, Duration.ofHours(8), 3, 4, 6, 15, seed);
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
        eventThread.execute(() -> subscriptions.forEach(this::emitHistory));
    }

    @Override
    public void subscribeCandles(ExchangeSymbol symbol, ExchangeTimeframe timeframe, ExchangeCandleListener listener) {
        Objects.requireNonNull(symbol, "symbol");
        Objects.requireNonNull(timeframe, "timeframe");
        Objects.requireNonNull(listener, "listener");
        NobitexExchangeMapper.toNobitexResolution(timeframe); // same validation as the real stream
        subscriptions.add(new Subscription(symbol, timeframe, listener));
    }

    @Override
    public void close() {
        eventThread.shutdownNow();
    }

    private void emitHistory(Subscription subscription) {
        Duration step = subscription.timeframe.duration();
        long stepSeconds = step.toSeconds();
        long startEpoch = Instant.now().minus(startAgo).getEpochSecond();
        Instant open = Instant.ofEpochSecond(startEpoch - Math.floorMod(startEpoch, stepSeconds));

        // gap position i = "missing candles right before the i-th emitted candle" (never before the first one)
        Map<Integer, Integer> gapsBefore = new TreeMap<>();
        int gapCount = minGaps + random.nextInt(maxGaps - minGaps + 1);
        while (gapsBefore.size() < gapCount) {
            gapsBefore.putIfAbsent(1 + random.nextInt(candleCount - 1),
                    minGapCandles + random.nextInt(maxGapCandles - minGapCandles + 1));
        }

        BigDecimal price = START_PRICES.getOrDefault(subscription.symbol.toString(), DEFAULT_START_PRICE);
        List<String> generatedGaps = new ArrayList<>();
        int emitted = 0;
        for (int i = 0; i < candleCount; i++) {
            Integer missing = gapsBefore.get(i);
            if (missing != null) {
                Instant gapTo = open.plus(step.multipliedBy(missing - 1L));
                generatedGaps.add("[" + open + " .. " + gapTo + "] (" + missing + ")");
                open = open.plus(step.multipliedBy(missing));
            }
            Instant close = open.plus(step);
            ExchangeCandle candle = nextCandle(open, close, price);
            price = candle.close();
            try {
                subscription.listener.onCandleClosed(subscription.symbol, subscription.timeframe, candle);
                emitted++;
            } catch (RuntimeException e) {
                log.warn("Gap mock listener failed for {} {}", subscription.symbol, subscription.timeframe, e);
            }
            open = close;
        }
        log.info("Gap MOCK {} {}: emitted {} candles, last open {}, generated gaps: {}", subscription.symbol,
                subscription.timeframe, emitted, open.minus(step), generatedGaps);
    }

    private ExchangeCandle nextCandle(Instant open, Instant close, BigDecimal lastClose) {
        int scale = Math.max(lastClose.scale(), 2);
        BigDecimal openPrice = move(lastClose, scale);
        BigDecimal closePrice = move(openPrice, scale);
        BigDecimal high = openPrice.max(closePrice)
                .multiply(BigDecimal.valueOf(1 + random.nextDouble() * MAX_MOVE / 2)).setScale(scale, RoundingMode.HALF_UP);
        BigDecimal low = openPrice.min(closePrice)
                .multiply(BigDecimal.valueOf(1 - random.nextDouble() * MAX_MOVE / 2)).setScale(scale, RoundingMode.HALF_UP);
        BigDecimal volume = BigDecimal.valueOf(random.nextDouble() * 0.05).setScale(6, RoundingMode.HALF_UP);
        return new ExchangeCandle(open, close, openPrice, high, low, closePrice, volume, true);
    }

    private BigDecimal move(BigDecimal price, int scale) {
        double factor = 1 + (random.nextDouble() * 2 - 1) * MAX_MOVE;
        return price.multiply(BigDecimal.valueOf(factor)).setScale(scale, RoundingMode.HALF_UP);
    }

    private record Subscription(ExchangeSymbol symbol, ExchangeTimeframe timeframe, ExchangeCandleListener listener) {
    }
}
