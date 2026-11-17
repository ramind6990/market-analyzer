package com.rmrx.marketnexus.marketdata.service.reconciliation;

import com.rmrx.marketnexus.marketdata.model.Timeframe;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** {@link MarketSeriesRepository} on PostgreSQL with plain JDBC ({@link JdbcClient}). */
@Repository
public class JdbcMarketSeriesRepository implements MarketSeriesRepository {

    private static final String INSERT_IF_ABSENT = """
            INSERT INTO market_series (exchange, symbol, timeframe)
            VALUES (CAST(:exchange AS exchange_type), :symbol, :timeframe)
            ON CONFLICT (exchange, symbol, timeframe) DO NOTHING
            """;

    private static final String SELECT = """
            SELECT exchange::text AS exchange, symbol, timeframe, verification_watermark, last_checked_at
            FROM market_series
            WHERE exchange = CAST(:exchange AS exchange_type) AND symbol = :symbol AND timeframe = :timeframe
            """;

    private static final String UPDATE_WATERMARK = """
            UPDATE market_series
            SET verification_watermark = :watermark, last_checked_at = :checkedAt, updated_at = now()
            WHERE exchange = CAST(:exchange AS exchange_type) AND symbol = :symbol AND timeframe = :timeframe
            """;

    private static final String INSERT_MISSING = """
            INSERT INTO market_series (exchange, symbol, timeframe)
            SELECT CAST(:exchange AS exchange_type), s.symbol, :timeframe
            FROM unnest(CAST(:symbols AS text[])) AS s(symbol)
            ON CONFLICT (exchange, symbol, timeframe) DO NOTHING
            """;

    private static final String SELECT_WATERMARKS = """
            SELECT symbol, verification_watermark
            FROM market_series
            WHERE exchange = CAST(:exchange AS exchange_type) AND timeframe = :timeframe
              AND symbol = ANY(CAST(:symbols AS text[]))
            """;

    private final JdbcClient jdbc;

    public JdbcMarketSeriesRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Map<String, Instant> findWatermarks(String exchange, Timeframe timeframe, List<String> symbols) {
        String[] symbolArray = symbols.toArray(String[]::new);
        jdbc.sql(INSERT_MISSING)
                .param("exchange", exchange)
                .param("timeframe", timeframe.name())
                .param("symbols", symbolArray)
                .update();
        Map<String, Instant> stored = new HashMap<>();
        jdbc.sql(SELECT_WATERMARKS)
                .param("exchange", exchange)
                .param("timeframe", timeframe.name())
                .param("symbols", symbolArray)
                .query(rs -> {
                    stored.put(rs.getString("symbol"), instant(rs, "verification_watermark"));
                });
        Map<String, Instant> watermarks = new LinkedHashMap<>();
        symbols.forEach(symbol -> watermarks.put(symbol, stored.get(symbol)));
        return watermarks;
    }

    @Override
    public MarketSeries findOrCreate(String exchange, String symbol, Timeframe timeframe) {
        jdbc.sql(INSERT_IF_ABSENT)
                .param("exchange", exchange)
                .param("symbol", symbol)
                .param("timeframe", timeframe.name())
                .update();
        return jdbc.sql(SELECT)
                .param("exchange", exchange)
                .param("symbol", symbol)
                .param("timeframe", timeframe.name())
                .query((rs, row) -> map(rs))
                .single();
    }

    @Override
    public void updateWatermark(String exchange, String symbol, Timeframe timeframe, Instant watermark, Instant checkedAt) {
        jdbc.sql(UPDATE_WATERMARK)
                .param("exchange", exchange)
                .param("symbol", symbol)
                .param("timeframe", timeframe.name())
                .param("watermark", watermark == null ? null : Timestamp.from(watermark))
                .param("checkedAt", Timestamp.from(checkedAt))
                .update();
    }

    private static MarketSeries map(ResultSet rs) throws SQLException {
        return new MarketSeries(
                rs.getString("exchange"),
                rs.getString("symbol"),
                Timeframe.valueOf(rs.getString("timeframe")),
                instant(rs, "verification_watermark"),
                instant(rs, "last_checked_at"));
    }

    static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
