package com.rmrx.marketnexus.marketanalysis.job.compute;

import com.rmrx.marketnexus.marketanalysis.technical.indicator.compute.IndicatorRow;
import com.rmrx.marketnexus.marketdata.model.Candle;

import java.util.List;

/**
 * What one job computes from.
 *
 * @param mode    SEEDED: continue from {@code rows} ({@code historyBars} stored rows, the newest is the seed);
 *                FULL: no usable seed, compute every candle, the first {@code maxWarmup} rows are not reliable
 * @param candles ascending, gap-free; SEEDED: starts at the oldest seed row
 */
public record JobInput(Mode mode, List<Candle> candles, List<IndicatorRow> rows) {

    public enum Mode { SEEDED, FULL }
}
