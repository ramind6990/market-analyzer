package com.rmrx.marketnexus.marketdata.service.tracking.rebuild;

import com.rmrx.marketnexus.marketdata.service.tracking.book.ContiguousRun;
import com.rmrx.marketnexus.marketdata.service.tracking.book.TrackingBook;
import com.rmrx.marketnexus.marketdata.service.tracking.tracker.SeriesKey;
import com.rmrx.marketnexus.marketdata.service.support.JdbcTimes;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

/**
 * Contiguous runs of stored closed candles of one series (startup rebuild of the {@link TrackingBook}).
 * <p>
 * Gaps-and-islands: within a run, {@code open_time − row_number × timeframe} is constant, so grouping by it yields
 * one row per run.
 */
@Repository
public class ContiguousRunQuery {

    private static final String RUNS = """
            SELECT min(open_time) AS run_from, max(open_time) AS run_to
            FROM (
                SELECT open_time,
                       open_time - row_number() OVER (ORDER BY open_time) * make_interval(secs => :stepSeconds) AS run_key
                FROM candle
                WHERE exchange = CAST(:exchange AS exchange_type) AND symbol = :symbol AND timeframe = :timeframe
                  AND closed AND open_time >= :since
            ) c
            GROUP BY run_key
            ORDER BY run_from
            """;

    private final JdbcClient jdbc;

    public ContiguousRunQuery(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Runs of closed candles with {@code open_time >= since}, oldest first. */
    public List<ContiguousRun> findRuns(SeriesKey series, Instant since) {
        return jdbc.sql(RUNS)
                .param("exchange", series.exchange())
                .param("symbol", series.symbol())
                .param("timeframe", series.timeframe().name())
                .param("stepSeconds", series.timeframe().duration().toSeconds())
                .param("since", Timestamp.from(since))
                .query((rs, row) -> new ContiguousRun(JdbcTimes.instant(rs, "run_from"), JdbcTimes.instant(rs, "run_to")))
                .list();
    }
}
