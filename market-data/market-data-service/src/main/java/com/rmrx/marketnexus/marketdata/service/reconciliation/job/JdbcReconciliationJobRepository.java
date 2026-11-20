package com.rmrx.marketnexus.marketdata.service.reconciliation.job;

import com.rmrx.marketnexus.marketdata.service.support.JdbcTimes;

import com.rmrx.marketnexus.marketdata.model.Timeframe;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.ToIntFunction;

/** {@link ReconciliationJobRepository} on PostgreSQL with plain JDBC ({@link JdbcClient}). */
@Repository
public class JdbcReconciliationJobRepository implements ReconciliationJobRepository {

    private static final String SELECT_OPEN = """
            SELECT symbol, window_from, window_to
            FROM reconciliation_job
            WHERE exchange = CAST(:exchange AS exchange_type) AND timeframe = :timeframe
              AND symbol = ANY(CAST(:symbols AS text[]))
              AND status IN ('PENDING', 'RUNNING')
            ORDER BY symbol, window_from
            """;

    private static final String INSERT = """
            INSERT INTO reconciliation_job (exchange, symbol, timeframe, window_from, window_to, cursor_at, priority)
            VALUES (CAST(:exchange AS exchange_type), :symbol, :timeframe, :windowFrom, :windowTo, :windowFrom, :priority)
            """;

    private static final String COLUMNS =
            "id, exchange::text AS exchange, symbol, timeframe, window_from, window_to, cursor_at, "
                    + "status::text AS status, priority, attempts, next_run_at, last_error";

    private static final String SELECT_BY_ID = "SELECT " + COLUMNS + " FROM reconciliation_job WHERE id = :id";

    private static final String CLAIM_BATCH = """
            WITH next AS (
                SELECT id FROM reconciliation_job
                WHERE status = 'PENDING' AND next_run_at <= now()
                ORDER BY priority, next_run_at, id
                LIMIT :limit
                FOR UPDATE SKIP LOCKED
            )
            UPDATE reconciliation_job j
            SET status = 'RUNNING', updated_at = now()
            FROM next
            WHERE j.id = next.id
            RETURNING j.id, j.exchange::text AS exchange, j.symbol, j.timeframe, j.window_from, j.window_to, j.cursor_at,
                      j.status::text AS status, j.priority, j.attempts, j.next_run_at, j.last_error
            """;

    private static final String RELEASE =
            "UPDATE reconciliation_job SET status = 'PENDING', updated_at = now() "
                    + "WHERE status = 'RUNNING' AND id = ANY(CAST(:ids AS bigint[]))";

    private static final String RESET_RUNNING =
            "UPDATE reconciliation_job SET status = 'PENDING', updated_at = now() WHERE status = 'RUNNING'";

    private static final String SET_STATUS = """
            UPDATE reconciliation_job
            SET status = CAST(:status AS job_status), last_error = :error, updated_at = now()
            WHERE id = :id
            """;

    private static final String ADVANCE = """
            UPDATE reconciliation_job
            SET cursor_at = :cursor, status = CAST(:status AS job_status), last_error = NULL, updated_at = now()
            WHERE id = :id
            """;

    private static final String SET_ATTEMPT = """
            UPDATE reconciliation_job
            SET status = CAST(:status AS job_status), attempts = :attempts, next_run_at = :nextRunAt,
                last_error = :error, updated_at = now()
            WHERE id = :id
            """;

    private final JdbcClient jdbc;

    public JdbcReconciliationJobRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<ReconciliationJob> findById(long id) {
        return jdbc.sql(SELECT_BY_ID).param("id", id).query((rs, row) -> map(rs)).optional();
    }

    @Override
    public List<ReconciliationJob> claimBatch(int limit) {
        List<ReconciliationJob> jobs = new ArrayList<>(jdbc.sql(CLAIM_BATCH)
                .param("limit", limit)
                .query((rs, row) -> map(rs))
                .list());
        // RETURNING has no guaranteed order: restore execution order
        jobs.sort(Comparator.comparingInt(ReconciliationJob::priority)
                .thenComparing(ReconciliationJob::nextRunAt)
                .thenComparingLong(ReconciliationJob::id));
        return jobs;
    }

    @Override
    public void release(List<Long> ids) {
        if (ids.isEmpty()) {
            return;
        }
        jdbc.sql(RELEASE).param("ids", ids.toArray(Long[]::new)).update();
    }

    @Override
    public int resetRunning() {
        return jdbc.sql(RESET_RUNNING).update();
    }

    private static ReconciliationJob map(ResultSet rs) throws SQLException {
        return new ReconciliationJob(
                rs.getLong("id"),
                rs.getString("exchange"),
                rs.getString("symbol"),
                Timeframe.valueOf(rs.getString("timeframe")),
                JdbcTimes.instant(rs, "window_from"),
                JdbcTimes.instant(rs, "window_to"),
                JdbcTimes.instant(rs, "cursor_at"),
                JobStatus.valueOf(rs.getString("status")),
                rs.getInt("priority"),
                rs.getInt("attempts"),
                JdbcTimes.instant(rs, "next_run_at"),
                rs.getString("last_error"));
    }

    @Override
    public void markRunning(long id) {
        setStatus(id, JobStatus.RUNNING, null);
    }

    @Override
    public void skip(long id, String reason) {
        setStatus(id, JobStatus.SKIPPED, reason);
    }

    @Override
    public void advance(long id, Instant cursor, JobStatus status) {
        jdbc.sql(ADVANCE)
                .param("id", id)
                .param("cursor", Timestamp.from(cursor))
                .param("status", status.name())
                .update();
    }

    @Override
    public void recordFailure(long id, JobStatus status, int attempts, Instant nextRunAt, String error) {
        jdbc.sql(SET_ATTEMPT)
                .param("id", id)
                .param("status", status.name())
                .param("attempts", attempts)
                .param("nextRunAt", Timestamp.from(nextRunAt))
                .param("error", error)
                .update();
    }

    private void setStatus(long id, JobStatus status, String error) {
        jdbc.sql(SET_STATUS)
                .param("id", id)
                .param("status", status.name())
                .param("error", error)
                .update();
    }

    @Override
    public Map<String, List<FetchWindow>> findOpenWindows(String exchange, Timeframe timeframe, List<String> symbols) {
        Map<String, List<FetchWindow>> bySymbol = new HashMap<>();
        jdbc.sql(SELECT_OPEN)
                .param("exchange", exchange)
                .param("timeframe", timeframe.name())
                .param("symbols", symbols.toArray(String[]::new))
                .query(rs -> {
                    bySymbol.computeIfAbsent(rs.getString("symbol"), symbol -> new ArrayList<>())
                            .add(new FetchWindow(JdbcTimes.instant(rs, "window_from"),
                                    JdbcTimes.instant(rs, "window_to")));
                });
        return bySymbol;
    }

    @Override
    public void insertAll(String exchange, String symbol, Timeframe timeframe, List<FetchWindow> windows,
                          ToIntFunction<FetchWindow> priority) {
        for (FetchWindow window : windows) {
            jdbc.sql(INSERT)
                    .param("exchange", exchange)
                    .param("symbol", symbol)
                    .param("timeframe", timeframe.name())
                    .param("windowFrom", Timestamp.from(window.from()))
                    .param("windowTo", Timestamp.from(window.to()))
                    .param("priority", priority.applyAsInt(window))
                    .update();
        }
    }
}
