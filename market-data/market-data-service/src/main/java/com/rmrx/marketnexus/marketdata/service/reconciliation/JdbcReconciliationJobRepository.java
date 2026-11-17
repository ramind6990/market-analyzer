package com.rmrx.marketnexus.marketdata.service.reconciliation;

import com.rmrx.marketnexus.marketdata.model.Timeframe;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
            INSERT INTO reconciliation_job (exchange, symbol, timeframe, window_from, window_to, cursor_at)
            VALUES (CAST(:exchange AS exchange_type), :symbol, :timeframe, :windowFrom, :windowTo, :windowFrom)
            """;

    private final JdbcClient jdbc;

    public JdbcReconciliationJobRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
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
                            .add(new FetchWindow(JdbcMarketSeriesRepository.instant(rs, "window_from"),
                                    JdbcMarketSeriesRepository.instant(rs, "window_to")));
                });
        return bySymbol;
    }

    @Override
    public void insertAll(String exchange, String symbol, Timeframe timeframe, List<FetchWindow> windows) {
        for (FetchWindow window : windows) {
            jdbc.sql(INSERT)
                    .param("exchange", exchange)
                    .param("symbol", symbol)
                    .param("timeframe", timeframe.name())
                    .param("windowFrom", Timestamp.from(window.from()))
                    .param("windowTo", Timestamp.from(window.to()))
                    .update();
        }
    }
}
