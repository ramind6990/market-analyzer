package com.rmrx.marketnexus.marketdata.service.reconciliation;

import com.rmrx.marketnexus.marketdata.model.Timeframe;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Checks series in the {@code candle} table for missing candles after given watermarks and returns the gaps found.
 * Read-only: it does not store anything; the caller decides what to do with the gaps.
 * <p>
 * Checked range per symbol: open times in {@code (start, upTo]} where
 * <ul>
 *   <li>{@code start} = the given watermark, or (if {@code null}) one timeframe before the oldest stored candle;</li>
 *   <li>{@code upTo} = the last closed candle, i.e. one before the ongoing one (the ongoing candle is not closed yet).
 *       A just-closed candle that has not arrived from the stream yet is reported as a gap; that is harmless, since
 *       repairs are upserts.</li>
 * </ul>
 * All symbols are checked with a single query ({@link CandleGapQuery}).
 */
@Service
public class GapDetectionService {

    private final CandleGapQuery gapQuery;
    private final Clock clock;

    @Autowired
    public GapDetectionService(CandleGapQuery gapQuery) {
        this(gapQuery, Clock.systemUTC());
    }

    GapDetectionService(CandleGapQuery gapQuery, Clock clock) {
        this.gapQuery = gapQuery;
        this.clock = clock;
    }

    /**
     * Checks several symbols of one exchange and timeframe at once.
     *
     * @param watermarks symbol → open time up to which that series is already verified gap-free ({@code null} = never checked)
     * @return one result per symbol, in the map's iteration order
     */
    public List<GapCheckResult> check(String exchange, Timeframe timeframe, Map<String, Instant> watermarks) {
        if (watermarks.isEmpty()) {
            return List.of();
        }
        return gapQuery.findGaps(exchange, timeframe, watermarks, clock.instant());
    }

    /** Checks a single symbol. */
    public GapCheckResult check(String exchange, String symbol, Timeframe timeframe, Instant watermark) {
        Map<String, Instant> watermarks = new HashMap<>();
        watermarks.put(symbol, watermark);
        return check(exchange, timeframe, watermarks).getFirst();
    }

}
