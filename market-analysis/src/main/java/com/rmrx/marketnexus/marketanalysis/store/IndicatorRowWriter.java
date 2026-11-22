package com.rmrx.marketnexus.marketanalysis.store;

import com.rmrx.marketnexus.marketanalysis.job.SeriesKey;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.compute.IndicatorRow;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.List;

/**
 * Upserts computed rows into {@code analysis.indicator_value}. Idempotent: the same bar of the same layout written
 * again (rerun, Kafka redelivery, rebalance overlap) replaces the row; rows of other layouts are never touched.
 */
@Repository
public class IndicatorRowWriter {

    private static final String UPSERT = """
            INSERT INTO analysis.indicator_value
                (exchange, symbol, timeframe, open_time, layout_version, node_values)
            VALUES (:exchange, :symbol, :timeframe, :openTime, :layoutVersion, :nodeValues)
            ON CONFLICT (exchange, symbol, timeframe, open_time, layout_version) DO UPDATE
                SET node_values    = EXCLUDED.node_values,
                    updated_at     = now()
            """;

    private static final int BATCH = 500;

    private final NamedParameterJdbcTemplate jdbc;

    public IndicatorRowWriter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** All rows in one transaction, so a job's rows are stored completely or not at all. */
    @Transactional
    public void upsert(SeriesKey series, List<IndicatorRow> rows) {
        for (int from = 0; from < rows.size(); from += BATCH) {
            List<IndicatorRow> chunk = rows.subList(from, Math.min(rows.size(), from + BATCH));
            jdbc.batchUpdate(UPSERT, chunk.stream().map(row -> params(series, row))
                    .toArray(SqlParameterSource[]::new));
        }
    }

    private static SqlParameterSource params(SeriesKey series, IndicatorRow row) {
        double[] values = row.values();
        Double[] boxed = new Double[values.length];
        for (int i = 0; i < values.length; i++) {
            boxed[i] = values[i];
        }
        return new MapSqlParameterSource()
                .addValue("exchange", series.exchange())
                .addValue("symbol", series.symbol())
                .addValue("timeframe", series.timeframe().name())
                .addValue("openTime", Timestamp.from(row.openTime()))
                .addValue("layoutVersion", row.layoutVersion())
                .addValue("nodeValues", boxed);
    }
}
