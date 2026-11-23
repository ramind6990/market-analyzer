package com.rmrx.marketnexus.marketdata.aggregator.local;

import com.rmrx.marketnexus.marketdata.aggregator.CandlePipeline;
import com.rmrx.marketnexus.marketdata.aggregator.model.Candle;
import com.rmrx.marketnexus.marketdata.aggregator.model.CandleUpdate;
import com.rmrx.marketnexus.marketdata.aggregator.model.Timeframe;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.util.CloseableIterator;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Runs {@link CandlePipeline} on an embedded Flink MiniCluster with a mock source (generated live M1 for one market)
 * and a mock sink (results collected back into this process). Just run {@code main} in IntelliJ — no Kafka needed.
 * <p>
 * Optional program arguments: {@code [m1Count] [seed] [dump]} (defaults 2000, 42; {@code dump} prints every update).
 */
public final class LocalAggregationRunner {

    private static final String EXCHANGE = "CRYPTO_NOBITEX";
    private static final String SYMBOL = "BTC/USDT";
    /**
     * A D1 boundary (20:30 UTC) two days ago, so every timeframe starts on its grid. It must be recent: windows are
     * purged at {@code windowStart + 7 days} of processing (wall-clock) time.
     */
    private static final Instant START = Instant.ofEpochMilli(
            Timeframe.D1.windowStart(EXCHANGE, Instant.now().minus(Duration.ofDays(2))));

    public static void main(String[] args) throws Exception {
        int count = args.length > 0 ? Integer.parseInt(args[0]) : 2000;
        long seed = args.length > 1 ? Long.parseLong(args[1]) : 42;
        boolean dump = args.length > 2 && args[2].equals("dump");
        List<Candle> m1 = MockCandles.m1(EXCHANGE, SYMBOL, START, count, seed);

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);
        TypeInformation<Candle> type = TypeInformation.of(Candle.class);

        List<CandleUpdate> output = new ArrayList<>();
        try (CloseableIterator<CandleUpdate> it = CandlePipeline.build(
                env.fromData(m1, type),
                none(env, m1, type),
                none(env, m1, type)).executeAndCollect("local-candle-aggregation")) {
            it.forEachRemaining(output::add);
        }

        if (dump) {
            output.forEach(System.out::println);
        }
        report(m1, output);
    }

    /** An empty input (Flink's collection source rejects empty collections). */
    private static DataStream<Candle> none(StreamExecutionEnvironment env, List<Candle> any,
                                           TypeInformation<Candle> type) {
        return env.fromData(any.subList(0, 1), type).filter(candle -> false);
    }

    private static void report(List<Candle> m1, List<CandleUpdate> output) {
        Map<Timeframe, Integer> progressive = new EnumMap<>(Timeframe.class);
        Map<Timeframe, List<CandleUpdate>> closed = new EnumMap<>(Timeframe.class);
        Set<String> closedKeys = new HashSet<>();
        int updatesAfterClose = 0;
        for (CandleUpdate c : output) {
            String key = c.timeframe() + "@" + c.openTime();
            if (closedKeys.contains(key)) {
                updatesAfterClose++;
            }
            if (c.closed()) {
                closed.computeIfAbsent(c.timeframe(), tf -> new ArrayList<>()).add(c);
                closedKeys.add(key);
            } else {
                progressive.merge(c.timeframe(), 1, Integer::sum);
            }
        }

        System.out.printf("%nInput: %d live M1 for %s:%s from %s%n%n", m1.size(), EXCHANGE, SYMBOL, START);
        System.out.println("tf    progressive  closed  expected");
        int mismatches = 0;
        for (Timeframe tf : Timeframe.values()) {
            if (tf == Timeframe.M1) {
                continue;
            }
            List<CandleUpdate> done = closed.getOrDefault(tf, List.of());
            long expected = m1.size() / tf.duration().toMinutes();
            System.out.printf("%-5s %11d  %6d  %8d%s%n", tf, progressive.getOrDefault(tf, 0), done.size(), expected,
                    done.size() == expected ? "" : "   <-- MISMATCH");
            for (CandleUpdate c : done) {
                mismatches += crossCheck(c, m1);
            }
        }

        System.out.println("\nClosed H1 and above:");
        for (Timeframe tf : List.of(Timeframe.H1, Timeframe.H4, Timeframe.H12, Timeframe.D1)) {
            for (CandleUpdate c : closed.getOrDefault(tf, List.of())) {
                System.out.printf("  %-4s %s  O %s  H %s  L %s  C %s  V %s%n", c.timeframe(), c.openTime(),
                        c.open(), c.high(), c.low(), c.close(), c.volume().stripTrailingZeros().toPlainString());
            }
        }

        System.out.printf("%nUpdates after a candle closed: %d%n", updatesAfterClose);
        System.out.printf("Closed candles differing from a direct M1 recomputation: %d%n", mismatches);
    }

    /** Recomputes a closed candle directly from the raw M1 (no cascade); returns 1 on mismatch, else 0. */
    private static int crossCheck(CandleUpdate c, List<Candle> m1) {
        Instant end = c.openTime().plus(c.timeframe().duration());
        List<Candle> children = m1.stream()
                .filter(x -> !x.openTime().isBefore(c.openTime()) && x.openTime().isBefore(end))
                .toList();
        List<Candle> traded = children.stream().filter(x -> x.volume().signum() > 0).toList();
        BigDecimal volume = children.stream().map(Candle::volume).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal open, high, low, close;
        if (traded.isEmpty()) {
            open = high = low = close = children.getFirst().close();
        } else {
            open = traded.getFirst().open();
            close = traded.getLast().close();
            high = traded.stream().map(Candle::high).reduce(BigDecimal::max).orElseThrow();
            low = traded.stream().map(Candle::low).reduce(BigDecimal::min).orElseThrow();
        }
        boolean same = open.compareTo(c.open()) == 0 && high.compareTo(c.high()) == 0
                && low.compareTo(c.low()) == 0 && close.compareTo(c.close()) == 0
                && volume.compareTo(c.volume()) == 0;
        if (!same) {
            System.out.printf("  MISMATCH %s %s: got O %s H %s L %s C %s V %s, expected O %s H %s L %s C %s V %s%n",
                    c.timeframe(), c.openTime(), c.open(), c.high(), c.low(), c.close(), c.volume(),
                    open, high, low, close, volume);
        }
        return same ? 0 : 1;
    }
}
