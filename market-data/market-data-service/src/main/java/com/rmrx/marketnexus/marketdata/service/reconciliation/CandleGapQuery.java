package com.rmrx.marketnexus.marketdata.service.reconciliation;

import com.rmrx.marketnexus.marketdata.model.Timeframe;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Finds missing candles for many symbols of one exchange and timeframe in a single statement.
 * <p>
 * Per symbol: the checked range is {@code (after, upTo]} where {@code after} is the given watermark (or, if
 * {@code null}, one step before the oldest stored candle) and {@code upTo} is the last closed candle (one before the ongoing one), on the
 * series' own grid. Two sentinel rows (the range start, and one step past the range end) are added to the stored open
 * times, so a single {@code lag} pass finds leading, inner and trailing gaps alike. {@code LEFT JOIN LATERAL} keeps one
 * primary-key index range scan per symbol (rows come back already ordered by {@code open_time}, no sort), and every
 * symbol returns at least one row, even without gaps.
 */
@Component
public class CandleGapQuery {

    private static final String FIND_GAPS = """
            WITH req AS (
                SELECT s.symbol, s.after_epoch
                FROM unnest(CAST(:symbols AS text[]), CAST(:afters AS bigint[])) AS s(symbol, after_epoch)
            ),
            starts AS (
                SELECT r.symbol,
                       COALESCE(to_timestamp(r.after_epoch),
                                (SELECT min(c.open_time) FROM candle c
                                 WHERE c.exchange = CAST(:exchange AS exchange_type)
                                   AND c.symbol = r.symbol AND c.timeframe = :timeframe)
                                - make_interval(secs => :step)) AS after_t
                FROM req r
            ),
            bounds AS (
                SELECT s.symbol, s.after_t,
                       s.after_t + make_interval(secs =>
                           floor(extract(epoch FROM (to_timestamp(:now) - s.after_t)) / :step) * :step - :step) AS up_to
                FROM starts s
            )
            SELECT b.symbol, b.after_t, b.up_to,
                   g.prev_open + make_interval(secs => :step) AS gap_from,
                   g.open_time - make_interval(secs => :step) AS gap_to
            FROM bounds b
            LEFT JOIN LATERAL (
                SELECT pts.open_time, lag(pts.open_time) OVER (ORDER BY pts.open_time) AS prev_open
                FROM (SELECT b.after_t AS open_time
                      UNION ALL
                      SELECT c.open_time FROM candle c
                      WHERE c.exchange = CAST(:exchange AS exchange_type)
                        AND c.symbol = b.symbol AND c.timeframe = :timeframe
                        AND c.open_time > b.after_t AND c.open_time <= b.up_to
                      UNION ALL
                      SELECT b.up_to + make_interval(secs => :step)) pts
            ) g ON b.after_t IS NOT NULL
               AND g.prev_open IS NOT NULL
               AND g.open_time - g.prev_open > make_interval(secs => :step)
            ORDER BY b.symbol, gap_from NULLS FIRST
            """;

    private final JdbcClient jdbc;

    public CandleGapQuery(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * @param watermarks symbol → watermark ({@code null} = never checked, start at the oldest stored candle)
     * @param now        current time; the ongoing (not yet closed) candle is excluded relative to it
     * @return one result per requested symbol, in the order of {@code watermarks}
     */
    public List<GapCheckResult> findGaps(String exchange, Timeframe timeframe, Map<String, Instant> watermarks,
                                         Instant now) {
        String[] symbols = watermarks.keySet().toArray(String[]::new);
        Long[] afters = watermarks.values().stream()
                .map(watermark -> watermark == null ? null : watermark.getEpochSecond())
                .toArray(Long[]::new);

        Map<String, Builder> bySymbol = new LinkedHashMap<>();
        for (String symbol : symbols) {
            bySymbol.put(symbol, new Builder());
        }
        jdbc.sql(FIND_GAPS)
                .param("symbols", symbols)
                .param("afters", afters)
                .param("exchange", exchange)
                .param("timeframe", timeframe.name())
                .param("step", timeframe.duration().toSeconds())
                .param("now", now.getEpochSecond())
                .query(rs -> {
                    Builder builder = bySymbol.get(rs.getString("symbol"));
                    builder.after = JdbcMarketSeriesRepository.instant(rs, "after_t");
                    builder.upTo = JdbcMarketSeriesRepository.instant(rs, "up_to");
                    Instant gapFrom = JdbcMarketSeriesRepository.instant(rs, "gap_from");
                    if (gapFrom != null) {
                        builder.gaps.add(new Gap(gapFrom, JdbcMarketSeriesRepository.instant(rs, "gap_to")));
                    }
                });

        List<GapCheckResult> results = new ArrayList<>(bySymbol.size());
        bySymbol.forEach((symbol, builder) -> results.add(builder.after == null
                ? new GapCheckResult(exchange, symbol, timeframe, null, null, List.of())   // no candles stored
                : new GapCheckResult(exchange, symbol, timeframe, builder.after, builder.upTo, builder.gaps)));
        return results;
    }

    private static final class Builder {
        private Instant after;
        private Instant upTo;
        private final List<Gap> gaps = new ArrayList<>();
    }
}
