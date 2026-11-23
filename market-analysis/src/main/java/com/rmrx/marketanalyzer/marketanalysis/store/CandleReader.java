package com.rmrx.marketanalyzer.marketanalysis.store;

import com.rmrx.marketanalyzer.marketanalysis.job.SeriesKey;
import com.rmrx.marketanalyzer.marketdata.model.Candle;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Reads closed candles from market-data-service's {@code public.candle} table (read only). */
@Repository
public class CandleReader {

    private static final String COLUMNS = "SELECT open_time, open, high, low, close, volume FROM public.candle ";
    private static final String SERIES = """
            WHERE exchange = CAST(:exchange AS exchange_type) AND symbol = :symbol AND timeframe = :timeframe
              AND closed
            """;

    private static final String RANGE = COLUMNS + SERIES + """
              AND open_time >= :from AND open_time <= :to
            ORDER BY open_time
            LIMIT :limit
            """;

    private static final String LAST = COLUMNS + SERIES + """
              AND open_time <= :to
            ORDER BY open_time DESC
            LIMIT :limit
            """;

    private final JdbcClient jdbc;

    public CandleReader(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Candles with {@code from <= openTime <= to}, ascending, at most {@code limit}. */
    public List<Candle> range(SeriesKey series, Instant from, Instant to, int limit) {
        return jdbc.sql(RANGE)
                .params(seriesParams(series))
                .param("from", Timestamp.from(from))
                .param("to", Timestamp.from(to))
                .param("limit", limit)
                .query((rs, n) -> map(series, rs))
                .list();
    }

    /** The newest {@code limit} candles with {@code openTime <= to}, ascending. */
    public List<Candle> last(SeriesKey series, Instant to, int limit) {
        List<Candle> candles = new ArrayList<>(jdbc.sql(LAST)
                .params(seriesParams(series))
                .param("to", Timestamp.from(to))
                .param("limit", limit)
                .query((rs, n) -> map(series, rs))
                .list());
        return candles.reversed();
    }

    static Map<String, Object> seriesParams(SeriesKey series) {
        return Map.of("exchange", series.exchange(), "symbol", series.symbol(),
                "timeframe", series.timeframe().name());
    }

    private static Candle map(SeriesKey series, ResultSet rs) throws SQLException {
        return new Candle(series.exchange(), series.symbol(), series.timeframe(),
                rs.getTimestamp("open_time").toInstant(), rs.getBigDecimal("open"), rs.getBigDecimal("high"),
                rs.getBigDecimal("low"), rs.getBigDecimal("close"), rs.getBigDecimal("volume"));
    }
}
