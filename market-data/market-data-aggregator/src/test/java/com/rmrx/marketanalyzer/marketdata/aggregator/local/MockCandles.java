package com.rmrx.marketanalyzer.marketdata.aggregator.local;

import com.rmrx.marketanalyzer.marketdata.aggregator.model.Candle;
import com.rmrx.marketanalyzer.marketdata.aggregator.model.Timeframe;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Generates consecutive closed M1 candles for one market: seeded random walk, some v=0 filler minutes. */
final class MockCandles {

    private MockCandles() {
    }

    static List<Candle> m1(String exchange, String symbol, Instant start, int count, long seed) {
        Random random = new Random(seed);
        List<Candle> candles = new ArrayList<>(count);
        BigDecimal previousClose = new BigDecimal("100000.00");
        for (int i = 0; i < count; i++) {
            Instant openTime = start.plus(Duration.ofMinutes(i));
            if (random.nextInt(8) == 0) {
                // No-trade minute: Nobitex sends a filler candle at the previous close with volume 0.
                candles.add(new Candle(exchange, symbol, Timeframe.M1, openTime,
                        previousClose, previousClose, previousClose, previousClose, BigDecimal.ZERO));
                continue;
            }
            BigDecimal open = previousClose;
            BigDecimal close = step(open, random);
            BigDecimal high = open.max(close).add(abs(random, 20));
            BigDecimal low = open.min(close).subtract(abs(random, 20));
            BigDecimal volume = BigDecimal.valueOf(random.nextDouble() * 2).setScale(8, RoundingMode.HALF_UP)
                    .add(new BigDecimal("0.00000001"));
            candles.add(new Candle(exchange, symbol, Timeframe.M1, openTime, open, high, low, close, volume));
            previousClose = close;
        }
        return candles;
    }

    private static BigDecimal step(BigDecimal price, Random random) {
        return price.add(BigDecimal.valueOf(random.nextGaussian() * 30)).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal abs(Random random, double scale) {
        return BigDecimal.valueOf(random.nextDouble() * scale).setScale(2, RoundingMode.HALF_UP);
    }
}
