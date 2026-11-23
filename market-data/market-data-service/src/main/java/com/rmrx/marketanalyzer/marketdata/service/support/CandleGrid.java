package com.rmrx.marketanalyzer.marketdata.service.support;

import com.rmrx.marketanalyzer.marketdata.model.Timeframe;

import java.time.Duration;
import java.time.Instant;

/** Candle-grid arithmetic shared by registration and execution. */
public final class CandleGrid {

    private CandleGrid() {
    }

    /**
     * First open time that can still be fetched: {@code now − retention}, rounded up to the series' candle grid.
     *
     * @param reference any open time of the series (defines the grid, e.g. Nobitex hours start at :30 UTC)
     */
    public static Instant horizon(Instant now, Duration retention, Instant reference, Timeframe timeframe) {
        Instant raw = now.minus(retention);
        Instant down = alignDown(raw, reference, timeframe);
        return down.equals(raw) ? raw : down.plus(timeframe.duration());
    }

    /** Start of the candle containing {@code time}, on the grid defined by {@code reference}. */
    public static Instant alignDown(Instant time, Instant reference, Timeframe timeframe) {
        long step = timeframe.duration().toSeconds();
        long offset = Math.floorMod(time.getEpochSecond() - reference.getEpochSecond(), step);
        return Instant.ofEpochSecond(time.getEpochSecond() - offset);
    }
}
