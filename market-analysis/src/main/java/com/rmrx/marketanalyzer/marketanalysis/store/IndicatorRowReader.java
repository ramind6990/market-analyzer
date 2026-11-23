package com.rmrx.marketanalyzer.marketanalysis.store;

import com.rmrx.marketanalyzer.marketanalysis.job.SeriesKey;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.compute.IndicatorRow;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads stored indicator rows ({@code analysis.indicator_value}) used as seeds. One row per bar holds every node value
 * of the series (public outputs and internal seed state, in layout order), so seeding a job reads the newest
 * {@code historyBars} rows in one query: deep enough for indicators that read past values of other outputs.
 */
@Repository
public class IndicatorRowReader {

    private static final String LAST = """
            SELECT open_time, layout_version, node_values FROM analysis.indicator_value
            WHERE exchange = :exchange AND symbol = :symbol AND timeframe = :timeframe
              AND layout_version = :layoutVersion AND open_time <= :to
            ORDER BY open_time DESC
            LIMIT :limit
            """;

    private final JdbcClient jdbc;

    public IndicatorRowReader(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** The newest {@code limit} rows of the layout with {@code openTime <= to}, ascending. */
    public List<IndicatorRow> last(SeriesKey series, long layoutVersion, Instant to, int limit) {
        List<IndicatorRow> rows = new ArrayList<>(jdbc.sql(LAST)
                .params(CandleReader.seriesParams(series))
                .param("layoutVersion", layoutVersion)
                .param("to", Timestamp.from(to))
                .param("limit", limit)
                .query((rs, n) -> map(rs))
                .list());
        return rows.reversed();
    }

    private static IndicatorRow map(ResultSet rs) throws SQLException {
        Array array = rs.getArray("node_values");
        Double[] boxed = (Double[]) array.getArray();
        double[] values = new double[boxed.length];
        for (int i = 0; i < boxed.length; i++) {
            values[i] = boxed[i];
        }
        return new IndicatorRow(rs.getTimestamp("open_time").toInstant(), rs.getLong("layout_version"), values);
    }
}
