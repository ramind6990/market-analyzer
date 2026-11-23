package com.rmrx.marketanalyzer.marketanalysis.job.compute;

import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.compute.IndicatorRow;

import java.time.Instant;
import java.util.List;

/**
 * Outcome of one series job.
 *
 * @param mode         how it was computed
 * @param seedOpenTime newest stored row the job continued from; {@code null} for FULL
 * @param rows         exactly the rows to upsert, ascending. SEEDED: every bar after the seed (none had a row).
 *                     FULL: every bar after warmup (may replace rows of an old layout or a broken run).
 * @param more         candles were capped ({@code max-new-bars}): bars remain up to the trigger's complete point
 */
public record JobResult(JobInput.Mode mode, Instant seedOpenTime, List<IndicatorRow> rows, boolean more) {

    public JobResult {
        rows = List.copyOf(rows);
    }
}
