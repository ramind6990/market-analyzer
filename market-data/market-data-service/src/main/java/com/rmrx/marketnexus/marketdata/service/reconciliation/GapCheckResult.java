package com.rmrx.marketnexus.marketdata.service.reconciliation;

import com.rmrx.marketnexus.marketdata.model.Timeframe;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Outcome of one gap check of a series.
 *
 * @param checkedAfter the check covered candles opening after this time ({@code null} when nothing was checked)
 * @param upTo         ...up to and including this open time ({@code null} when nothing was checked)
 * @param gaps         missing runs, oldest first; empty when the range is complete
 */
public record GapCheckResult(
        String exchange,
        String symbol,
        Timeframe timeframe,
        Instant checkedAfter,
        Instant upTo,
        List<Gap> gaps) {

    public GapCheckResult {
        Objects.requireNonNull(exchange, "exchange");
        Objects.requireNonNull(symbol, "symbol");
        Objects.requireNonNull(timeframe, "timeframe");
        gaps = gaps == null ? List.of() : List.copyOf(gaps);
    }

    public boolean hasGaps() {
        return !gaps.isEmpty();
    }

    /** Total number of missing candles over all gaps. */
    public long missingCandles() {
        return gaps.stream().mapToLong(gap -> gap.missingCandles(timeframe)).sum();
    }
}
