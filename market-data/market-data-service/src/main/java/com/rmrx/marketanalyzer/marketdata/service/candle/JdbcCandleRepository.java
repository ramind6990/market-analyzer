package com.rmrx.marketanalyzer.marketdata.service.candle;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import com.rmrx.marketanalyzer.marketdata.model.Candle;

import java.sql.Timestamp;

/** {@link CandleRepository} on PostgreSQL with plain JDBC ({@link JdbcClient}). */
@Repository
public class JdbcCandleRepository implements CandleRepository {

    /** Insert, or update only when the values differ: an identical re-delivery changes nothing and reports 0 rows. */
    private static final String UPSERT = """
            INSERT INTO candle AS c (exchange, symbol, timeframe, open_time, open, high, low, close, volume, source)
            VALUES (CAST(:exchange AS exchange_type), :symbol, :timeframe, :openTime, :open, :high, :low, :close,
                    :volume, CAST(:source AS candle_source))
            ON CONFLICT (exchange, symbol, timeframe, open_time) DO UPDATE
            SET open = EXCLUDED.open, high = EXCLUDED.high, low = EXCLUDED.low, close = EXCLUDED.close,
                volume = EXCLUDED.volume, source = EXCLUDED.source, updated_at = now()
            WHERE (c.open, c.high, c.low, c.close, c.volume)
                  IS DISTINCT FROM (EXCLUDED.open, EXCLUDED.high, EXCLUDED.low, EXCLUDED.close, EXCLUDED.volume)
            """;

    /** Live candles come from the exchange stream; reconciled ones will be written as {@code POLL} later. */
    private static final String SOURCE_STREAM = "STREAM";

    private final JdbcClient jdbc;

    public JdbcCandleRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
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
                .param("source", SOURCE_STREAM)
                .update();
        return rows > 0;
    }
}
