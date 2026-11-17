package com.rmrx.marketnexus.marketdata.service.reconciliation;

import com.rmrx.marketnexus.marketdata.model.Timeframe;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Rules that turn detected gaps into reconciliation job windows. Pure functions, no database access.
 * <ol>
 *   <li>Clip gaps to the retention horizon: parts older than the exchange keeps candles can't be fetched.</li>
 *   <li>Move the watermark to just before the first recoverable gap (or to {@code upTo} if there is none).</li>
 *   <li>Pack nearby gaps into one window while its span stays within the limit; a larger gap stays one window
 *       (the worker fetches it in chunks).</li>
 *   <li>Subtract windows already covered by open jobs.</li>
 * </ol>
 */
final class GapPlanner {

    private GapPlanner() {
    }

    /** Gaps split at the horizon. */
    record Clipped(List<Gap> recoverable, List<Gap> unrecoverable) {
    }

    /**
     * First open time that can still be fetched: {@code now − retention}, rounded up to the series' candle grid.
     *
     * @param reference any open time of the series (defines the grid, e.g. Nobitex hours start at :30 UTC)
     */
    static Instant horizon(Instant now, Duration retention, Instant reference, Timeframe timeframe) {
        Instant raw = now.minus(retention);
        Instant down = alignDown(raw, reference, timeframe);
        return down.equals(raw) ? raw : down.plus(timeframe.duration());
    }

    /** Splits gaps into the parts at/after the horizon (recoverable) and before it (unrecoverable). */
    static Clipped clipToHorizon(List<Gap> gaps, Instant horizon, Timeframe timeframe) {
        List<Gap> recoverable = new ArrayList<>();
        List<Gap> unrecoverable = new ArrayList<>();
        for (Gap gap : gaps) {
            if (gap.to().isBefore(horizon)) {
                unrecoverable.add(gap);
            } else if (gap.from().isBefore(horizon)) {
                unrecoverable.add(new Gap(gap.from(), horizon.minus(timeframe.duration())));
                recoverable.add(new Gap(horizon, gap.to()));
            } else {
                recoverable.add(gap);
            }
        }
        return new Clipped(recoverable, unrecoverable);
    }

    /**
     * New watermark after a check: just before the first recoverable gap, or {@code upTo} when there is none
     * (unrecoverable gaps don't block it). Never moves backwards, never before the checked start.
     *
     * @return the new watermark, or {@code oldWatermark} if nothing was checked
     */
    static Instant newWatermark(Instant oldWatermark, GapCheckResult result, List<Gap> recoverable) {
        if (result.checkedAfter() == null) {
            return oldWatermark;
        }
        Instant candidate = recoverable.isEmpty()
                ? result.upTo()
                : recoverable.getFirst().from().minus(result.timeframe().duration());
        if (candidate.isBefore(result.checkedAfter())) {
            candidate = result.checkedAfter();
        }
        return oldWatermark != null && !candidate.isAfter(oldWatermark) ? oldWatermark : candidate;
    }

    /**
     * Packs consecutive gaps into windows: a gap joins the current window while the window's span (first gap start to
     * this gap's end) stays within {@code maxCandles}. One REST call returns every candle in a window, so the span
     * counts, not just the missing candles. A single gap larger than the limit stays one window.
     */
    static List<FetchWindow> pack(List<Gap> gaps, int maxCandles, Timeframe timeframe) {
        List<FetchWindow> windows = new ArrayList<>();
        Instant from = null;
        Instant to = null;
        for (Gap gap : gaps) {
            if (from == null) {
                from = gap.from();
                to = gap.to();
                continue;
            }
            long span = Duration.between(from, gap.to()).dividedBy(timeframe.duration()) + 1;
            if (span <= maxCandles) {
                to = gap.to();
            } else {
                windows.add(new FetchWindow(from, to));
                from = gap.from();
                to = gap.to();
            }
        }
        if (from != null) {
            windows.add(new FetchWindow(from, to));
        }
        return windows;
    }

    /** Removes the parts of {@code windows} already covered by {@code covered} (open jobs); partial overlaps leave the rest. */
    static List<FetchWindow> subtract(List<FetchWindow> windows, List<FetchWindow> covered, Timeframe timeframe) {
        Duration step = timeframe.duration();
        List<FetchWindow> remaining = new ArrayList<>(windows);
        for (FetchWindow cover : covered) {
            List<FetchWindow> next = new ArrayList<>();
            for (FetchWindow window : remaining) {
                if (cover.to().isBefore(window.from()) || cover.from().isAfter(window.to())) {
                    next.add(window);                                            // no overlap
                    continue;
                }
                if (cover.from().isAfter(window.from())) {
                    next.add(new FetchWindow(window.from(), cover.from().minus(step)));   // part before the cover
                }
                if (cover.to().isBefore(window.to())) {
                    next.add(new FetchWindow(cover.to().plus(step), window.to()));       // part after the cover
                }
            }
            remaining = next;
        }
        return remaining;
    }

    /** Start of the candle containing {@code time}, on the grid defined by {@code reference}. */
    static Instant alignDown(Instant time, Instant reference, Timeframe timeframe) {
        long step = timeframe.duration().toSeconds();
        long offset = Math.floorMod(time.getEpochSecond() - reference.getEpochSecond(), step);
        return Instant.ofEpochSecond(time.getEpochSecond() - offset);
    }
}
