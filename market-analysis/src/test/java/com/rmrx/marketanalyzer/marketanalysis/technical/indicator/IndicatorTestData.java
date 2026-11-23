package com.rmrx.marketanalyzer.marketanalysis.technical.indicator;

import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.compute.IndicatorRow;
import com.rmrx.marketanalyzer.marketdata.model.Candle;
import com.rmrx.marketanalyzer.marketdata.model.Timeframe;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Synthetic candles and comparison helpers shared by the indicator tests and the reference harness. */
final class IndicatorTestData {

    static final int SETS = 5;
    static final int CANDLES = 1000;
    static final double TOLERANCE = 1e-9;

    private IndicatorTestData() {
    }

    /**
     * 1,000 consecutive M1 candles (random walk, reproducible per set); every 97th bar is a flat zero-volume filler
     * candle like Nobitex sends for minutes without trades.
     */
    static List<Candle> candles(int set) {
        Random random = new Random(1000L + set);
        List<Candle> candles = new ArrayList<>(CANDLES);
        double price = 50_000 + 5_000 * set;
        Instant time = Instant.parse("2026-11-01T00:00:00Z").plus(Timeframe.M1.duration().multipliedBy(CANDLES * set));
        for (int i = 0; i < CANDLES; i++) {
            double open = price;
            double close = open;
            double high = open;
            double low = open;
            double volume = 0;
            if (i % 97 != 50) {
                close = open * (1 + random.nextGaussian() * 0.001);
                high = Math.max(open, close) * (1 + random.nextDouble() * 0.0005);
                low = Math.min(open, close) * (1 - random.nextDouble() * 0.0005);
                volume = 0.1 + random.nextDouble() * 5;
            }
            candles.add(new Candle("CRYPTO_DEMO", "BTC/USDT", Timeframe.M1, time,
                    BigDecimal.valueOf(open), BigDecimal.valueOf(high), BigDecimal.valueOf(low),
                    BigDecimal.valueOf(close), BigDecimal.valueOf(volume)));
            price = close;
            time = time.plus(Timeframe.M1.duration());
        }
        return candles;
    }

    /** Largest relative difference over all values (public and internal outputs); infinite on row mismatch. */
    static double compareRows(List<IndicatorRow> expected, List<IndicatorRow> actual) {
        if (expected.size() != actual.size()) {
            return Double.POSITIVE_INFINITY;
        }
        double maxDiff = 0;
        for (int i = 0; i < expected.size(); i++) {
            if (!expected.get(i).openTime().equals(actual.get(i).openTime())) {
                return Double.POSITIVE_INFINITY;
            }
            double[] e = expected.get(i).values();
            double[] a = actual.get(i).values();
            for (int c = 0; c < e.length; c++) {
                maxDiff = Math.max(maxDiff, relativeDiff(e[c], a[c]));
            }
        }
        return maxDiff;
    }

    static double relativeDiff(double expected, double actual) {
        if (Double.isNaN(expected) && Double.isNaN(actual)) {
            return 0;
        }
        if (Double.isNaN(expected) || Double.isNaN(actual)) {
            return Double.POSITIVE_INFINITY;
        }
        return Math.abs(expected - actual) / Math.max(1, Math.abs(expected));
    }
}
