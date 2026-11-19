package com.rmrx.marketnexus.marketdata.service.candle;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import com.rmrx.marketnexus.marketdata.model.Candle;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/** {@link CandleRepository} on PostgreSQL with plain JDBC ({@link JdbcClient}). */
@Repository
public class JdbcCandleRepository implements CandleRepository {

    /** Insert, or update only when the values differ: an identical re-delivery changes nothing and reports 0 rows. */
    private static final String UPSERT = """
            INSERT INTO candle AS c (exchange, symbol, timeframe, open_time, open, high, low, close, volume, source, closed)
            VALUES (CAST(:exchange AS exchange_type), :symbol, :timeframe, :openTime, :open, :high, :low, :close,
                    :volume, CAST(:source AS candle_source), true)
            ON CONFLICT (exchange, symbol, timeframe, open_time) DO UPDATE
            SET open = EXCLUDED.open, high = EXCLUDED.high, low = EXCLUDED.low, close = EXCLUDED.close,
                volume = EXCLUDED.volume, source = EXCLUDED.source, closed = EXCLUDED.closed, updated_at = now()
            WHERE (c.open, c.high, c.low, c.close, c.volume, c.closed)
                  IS DISTINCT FROM (EXCLUDED.open, EXCLUDED.high, EXCLUDED.low, EXCLUDED.close, EXCLUDED.volume,
                                    EXCLUDED.closed)
            """;

    /**
     * Many candles of one series in one statement (arrays unnested into rows). Only rows that were inserted or whose
     * values changed are returned; {@code xmax = 0} marks a freshly inserted row.
     */
    private static final String UPSERT_ALL = """
            INSERT INTO candle AS c (exchange, symbol, timeframe, open_time, open, high, low, close, volume, source, closed)
            SELECT CAST(:exchange AS exchange_type), :symbol, :timeframe, to_timestamp(u.open_epoch),
                   u.open, u.high, u.low, u.close, u.volume, CAST(:source AS candle_source), true
            FROM unnest(CAST(:openEpochs AS bigint[]), CAST(:opens AS numeric[]), CAST(:highs AS numeric[]),
                        CAST(:lows AS numeric[]), CAST(:closes AS numeric[]), CAST(:volumes AS numeric[]))
                 AS u(open_epoch, open, high, low, close, volume)
            ON CONFLICT (exchange, symbol, timeframe, open_time) DO UPDATE
            SET open = EXCLUDED.open, high = EXCLUDED.high, low = EXCLUDED.low, close = EXCLUDED.close,
                volume = EXCLUDED.volume, source = EXCLUDED.source, closed = EXCLUDED.closed, updated_at = now()
            WHERE (c.open, c.high, c.low, c.close, c.volume, c.closed)
                  IS DISTINCT FROM (EXCLUDED.open, EXCLUDED.high, EXCLUDED.low, EXCLUDED.close, EXCLUDED.volume,
                                    EXCLUDED.closed)
            RETURNING c.open_time, (c.xmax = 0) AS inserted
            """;

    /**
     * Many derived candles (source DERIVED), possibly of several series, in one statement. A row is updated only when
     * its values or {@code closed} differ, and a partial update ({@code closed = false}) never overwrites a closed row.
     * Returns the rows that were inserted or changed.
     */
    private static final String UPSERT_DERIVED_ALL = """
            INSERT INTO candle AS c (exchange, symbol, timeframe, open_time, open, high, low, close, volume, source, closed)
            SELECT CAST(u.exchange AS exchange_type), u.symbol, u.timeframe, to_timestamp(u.open_epoch),
                   u.open, u.high, u.low, u.close, u.volume, CAST('DERIVED' AS candle_source), u.closed
            FROM unnest(CAST(:exchanges AS text[]), CAST(:symbols AS text[]), CAST(:timeframes AS text[]),
                        CAST(:openEpochs AS bigint[]), CAST(:opens AS numeric[]), CAST(:highs AS numeric[]),
                        CAST(:lows AS numeric[]), CAST(:closes AS numeric[]), CAST(:volumes AS numeric[]),
                        CAST(:closeds AS boolean[]))
                 AS u(exchange, symbol, timeframe, open_epoch, open, high, low, close, volume, closed)
            ON CONFLICT (exchange, symbol, timeframe, open_time) DO UPDATE
            SET open = EXCLUDED.open, high = EXCLUDED.high, low = EXCLUDED.low, close = EXCLUDED.close,
                volume = EXCLUDED.volume, source = EXCLUDED.source, closed = EXCLUDED.closed, updated_at = now()
            WHERE NOT (c.closed AND NOT EXCLUDED.closed)
              AND (c.open, c.high, c.low, c.close, c.volume, c.closed)
                  IS DISTINCT FROM (EXCLUDED.open, EXCLUDED.high, EXCLUDED.low, EXCLUDED.close, EXCLUDED.volume,
                                    EXCLUDED.closed)
            RETURNING CAST(c.exchange AS text) AS exchange, c.symbol, c.timeframe, c.open_time
            """;


    private final JdbcClient jdbc;

    public JdbcCandleRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<UpsertedCandle> upsertAll(List<Candle> candles, CandleSource source) {
        if (candles.isEmpty()) {
            return List.of();
        }
        Candle first = candles.getFirst();
        // One row per open time (a statement may not update the same row twice); the last one wins.
        Map<Instant, Candle> byOpenTime = new LinkedHashMap<>();
        for (Candle candle : candles) {
            if (!candle.exchange().equals(first.exchange()) || !candle.symbol().equals(first.symbol())
                    || candle.timeframe() != first.timeframe()) {
                throw new IllegalArgumentException("upsertAll expects candles of one series, got " + first.symbol()
                        + " " + first.timeframe() + " and " + candle.symbol() + " " + candle.timeframe());
            }
            byOpenTime.put(candle.openTime(), candle);
        }
        List<Candle> rows = List.copyOf(byOpenTime.values());

        Map<Instant, Boolean> written = new HashMap<>();
        jdbc.sql(UPSERT_ALL)
                .param("exchange", first.exchange())
                .param("symbol", first.symbol())
                .param("timeframe", first.timeframe().name())
                .param("source", source.name())
                .param("openEpochs", rows.stream().map(c -> c.openTime().getEpochSecond()).toArray(Long[]::new))
                .param("opens", decimals(rows, Candle::open))
                .param("highs", decimals(rows, Candle::high))
                .param("lows", decimals(rows, Candle::low))
                .param("closes", decimals(rows, Candle::close))
                .param("volumes", decimals(rows, Candle::volume))
                .query(rs -> {
                    Instant openTime = rs.getObject("open_time", OffsetDateTime.class).toInstant();
                    written.put(openTime, rs.getBoolean("inserted"));
                });

        List<UpsertedCandle> result = new ArrayList<>(written.size());
        for (Candle candle : rows) {
            Boolean inserted = written.get(candle.openTime());
            if (inserted != null) {
                result.add(new UpsertedCandle(candle, inserted));
            }
        }
        return result;
    }

    /** Decimals as exact strings (cast to numeric[] in SQL), so no precision is lost on the way. */
    private static String[] decimals(List<Candle> rows, Function<Candle, BigDecimal> value) {
        return rows.stream().map(value).map(BigDecimal::toPlainString).toArray(String[]::new);
    }

    @Override
    public boolean upsert(Candle candle) {
        int rows = jdbc.sql(UPSERT)
                .param("exchange", candle.exchange())
                .param("symbol", candle.symbol())
                .param("timeframe", candle.timeframe().name())
                .param("openTime", Timestamp.from(candle.openTime()))
                .param("open", candle.open())
                .param("high", candle.high())
                .param("low", candle.low())
                .param("close", candle.close())
                .param("volume", candle.volume())
                .param("source", CandleSource.STREAM.name())
                .update();
        return rows > 0;
    }

    @Override
    public List<DerivedCandle> upsertDerivedAll(List<DerivedCandle> candles) {
        if (candles.isEmpty()) {
            return List.of();
        }
        // One row per candle (a statement may not update the same row twice); the last update wins.
        Map<String, DerivedCandle> byKey = new LinkedHashMap<>();
        for (DerivedCandle candle : candles) {
            byKey.remove(candle.rowKey());
            byKey.put(candle.rowKey(), candle);
        }
        List<DerivedCandle> rows = List.copyOf(byKey.values());
        List<Candle> plain = rows.stream().map(DerivedCandle::candle).toList();

        Set<String> written = new HashSet<>();
        jdbc.sql(UPSERT_DERIVED_ALL)
                .param("exchanges", plain.stream().map(Candle::exchange).toArray(String[]::new))
                .param("symbols", plain.stream().map(Candle::symbol).toArray(String[]::new))
                .param("timeframes", plain.stream().map(c -> c.timeframe().name()).toArray(String[]::new))
                .param("openEpochs", plain.stream().map(c -> c.openTime().getEpochSecond()).toArray(Long[]::new))
                .param("opens", decimals(plain, Candle::open))
                .param("highs", decimals(plain, Candle::high))
                .param("lows", decimals(plain, Candle::low))
                .param("closes", decimals(plain, Candle::close))
                .param("volumes", decimals(plain, Candle::volume))
                .param("closeds", rows.stream().map(DerivedCandle::closed).toArray(Boolean[]::new))
                .query(rs -> {
                    written.add(rs.getString("exchange") + ":" + rs.getString("symbol") + ":"
                            + rs.getString("timeframe") + ":"
                            + rs.getObject("open_time", OffsetDateTime.class).toEpochSecond());
                });
        return rows.stream().filter(row -> written.contains(row.rowKey())).toList();
    }
}
