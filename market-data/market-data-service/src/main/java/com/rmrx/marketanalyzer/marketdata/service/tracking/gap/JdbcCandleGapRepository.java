package com.rmrx.marketanalyzer.marketdata.service.tracking.gap;

import com.rmrx.marketanalyzer.marketdata.service.tracking.tracker.SeriesKey;
import com.rmrx.marketanalyzer.marketdata.model.Timeframe;
import com.rmrx.marketanalyzer.marketdata.service.support.JdbcTimes;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** {@link CandleGapRepository} on PostgreSQL with plain JDBC ({@link JdbcClient}). */
@Repository
public class JdbcCandleGapRepository implements CandleGapRepository {

    private static final String COLUMNS = """
            id, exchange::text AS exchange, symbol, timeframe, gap_from, gap_to, filled, detected_at, filled_at
            """;

    private static final String INSERT = """
            INSERT INTO candle_gap (exchange, symbol, timeframe, gap_from, gap_to, detected_at)
            VALUES (CAST(:exchange AS exchange_type), :symbol, :timeframe, :from, :to, :detectedAt)
            ON CONFLICT (exchange, symbol, timeframe, gap_from) DO UPDATE
            SET gap_to = GREATEST(candle_gap.gap_to, EXCLUDED.gap_to), updated_at = now()
            WHERE NOT candle_gap.filled
            RETURNING\s""" + COLUMNS;   // \s: a text block drops the trailing space otherwise ("RETURNINGid")

    private static final String SELECT_UNFILLED_BY_TIMEFRAME = "SELECT " + COLUMNS + """
            FROM candle_gap
            WHERE exchange = CAST(:exchange AS exchange_type) AND timeframe = :timeframe AND NOT filled
            ORDER BY symbol, gap_from
            """;

    private static final String SELECT_UNFILLED_BY_SERIES = "SELECT " + COLUMNS + """
            FROM candle_gap
            WHERE exchange = CAST(:exchange AS exchange_type) AND symbol = :symbol AND timeframe = :timeframe
              AND NOT filled
            ORDER BY gap_from
            """;

    private static final String MARK_FILLED = """
            UPDATE candle_gap SET filled = true, filled_at = :filledAt, updated_at = now()
            WHERE id = :id
            """;

    private final JdbcClient jdbc;

    public JdbcCandleGapRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<CandleGap> record(SeriesKey series, Instant from, Instant to, Instant detectedAt) {
        return jdbc.sql(INSERT)
                .param("exchange", series.exchange())
                .param("symbol", series.symbol())
                .param("timeframe", series.timeframe().name())
                .param("from", Timestamp.from(from))
                .param("to", Timestamp.from(to))
                .param("detectedAt", Timestamp.from(detectedAt))
                .query((rs, row) -> map(rs))
                .optional();
    }

    @Override
    public List<CandleGap> findUnfilled(String exchange, Timeframe timeframe) {
        return jdbc.sql(SELECT_UNFILLED_BY_TIMEFRAME)
                .param("exchange", exchange)
                .param("timeframe", timeframe.name())
                .query((rs, row) -> map(rs))
                .list();
    }

    @Override
    public List<CandleGap> findUnfilled(SeriesKey series) {
        return jdbc.sql(SELECT_UNFILLED_BY_SERIES)
                .param("exchange", series.exchange())
                .param("symbol", series.symbol())
                .param("timeframe", series.timeframe().name())
                .query((rs, row) -> map(rs))
                .list();
    }

    @Override
    public void markFilled(long id, Instant filledAt) {
        jdbc.sql(MARK_FILLED)
                .param("id", id)
                .param("filledAt", Timestamp.from(filledAt))
                .update();
    }

    private static CandleGap map(ResultSet rs) throws SQLException {
        return new CandleGap(
                rs.getLong("id"),
                new SeriesKey(rs.getString("exchange"), rs.getString("symbol"),
                        Timeframe.valueOf(rs.getString("timeframe"))),
                JdbcTimes.instant(rs, "gap_from"),
                JdbcTimes.instant(rs, "gap_to"),
                rs.getBoolean("filled"),
                JdbcTimes.instant(rs, "detected_at"),
                JdbcTimes.instant(rs, "filled_at"));
    }
}
