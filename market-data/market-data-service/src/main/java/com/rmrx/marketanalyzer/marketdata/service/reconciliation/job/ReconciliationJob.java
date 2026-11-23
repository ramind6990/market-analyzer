package com.rmrx.marketanalyzer.marketdata.service.reconciliation.job;

import com.rmrx.marketanalyzer.marketdata.model.Timeframe;

import java.time.Instant;
import java.util.Objects;

/**
 * One row of {@code reconciliation_job}: a window of candles to fetch, and how far fetching has got.
 *
 * @param windowFrom first open time to fetch (inclusive)
 * @param windowTo   last open time to fetch (inclusive)
 * @param cursor     next open time to fetch; the job is complete once it is past {@code windowTo}
 * @param priority   execution order, lower first (0 high, 1 medium, 2 low)
 * @param attempts   failed executions so far
 */
public record ReconciliationJob(
        long id,
        String exchange,
        String symbol,
        Timeframe timeframe,
        Instant windowFrom,
        Instant windowTo,
        Instant cursor,
        JobStatus status,
        int priority,
        int attempts,
        Instant nextRunAt,
        String lastError) {

    public ReconciliationJob {
        Objects.requireNonNull(exchange, "exchange");
        Objects.requireNonNull(symbol, "symbol");
        Objects.requireNonNull(timeframe, "timeframe");
        Objects.requireNonNull(windowFrom, "windowFrom");
        Objects.requireNonNull(windowTo, "windowTo");
        Objects.requireNonNull(cursor, "cursor");
        Objects.requireNonNull(status, "status");
    }

    @Override
    public String toString() {
        return "job#" + id + " " + exchange + " " + symbol + " " + timeframe + " [" + windowFrom + " .. " + windowTo
                + "] cursor=" + cursor + " " + status + " prio=" + priority + " attempts=" + attempts;
    }
}
