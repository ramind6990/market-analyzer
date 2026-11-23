package com.rmrx.marketanalyzer.marketanalysis.store;

import com.rmrx.marketanalyzer.marketanalysis.job.SeriesKey;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.compute.IndicatorRow;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * Newest stored rows of a series across all layouts, one per bar. A bar stored under several layouts (overlap after a
 * layout change recomputed the series) is returned from the newest layout.
 */
@Repository
public class IndicatorValueQuery {

    private static final String NEWEST = """
            SELECT DISTINCT ON (v.open_time) v.open_time, v.layout_version, v.node_values
            FROM analysis.indicator_value v
            JOIN analysis.indicator_layout l ON l.layout_version = v.layout_version
            WHERE v.exchange = :exchange AND v.symbol = :symbol AND v.timeframe = :timeframe
            """;

    private static final String ORDER = """
            ORDER BY v.open_time DESC, l.created_at DESC
            LIMIT :limit
            """;

    private static final String CONTAINING = "  AND l.entries @> CAST(:names AS text[])\n";

    private final JdbcClient jdbc;

    public IndicatorValueQuery(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * @param names only layouts containing all these names; {@code null} = any layout
     * @return at most {@code limit} rows, newest bar first
     */
    public List<IndicatorRow> newest(SeriesKey series, List<String> names, int limit) {
        JdbcClient.StatementSpec statement = jdbc.sql(NEWEST + (names == null ? "" : CONTAINING) + ORDER)
                .params(CandleReader.seriesParams(series))
                .param("limit", limit);
        if (names != null) {
            statement = statement.param("names", names.toArray(String[]::new));
        }
        return statement.query((rs, n) -> map(rs)).list();
    }

    private static IndicatorRow map(ResultSet rs) throws SQLException {
        Double[] boxed = (Double[]) rs.getArray("node_values").getArray();
        double[] values = new double[boxed.length];
        for (int i = 0; i < boxed.length; i++) {
            values[i] = boxed[i];
        }
        return new IndicatorRow(rs.getTimestamp("open_time").toInstant(), rs.getLong("layout_version"), values);
    }
}
