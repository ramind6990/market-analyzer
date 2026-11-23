package com.rmrx.marketanalyzer.marketanalysis.technical.indicator.compute;

import java.util.List;

/**
 * @param rows   newly computed rows, ascending: after the seed when seeded, otherwise one per candle
 * @param seeded whether the computation continued from stored rows; when false the first
 *               {@code IndicatorGraph.maxWarmup()} rows are not yet reliable
 */
public record ComputationResult(List<IndicatorRow> rows, boolean seeded) {

    public ComputationResult {
        rows = List.copyOf(rows);
    }
}
