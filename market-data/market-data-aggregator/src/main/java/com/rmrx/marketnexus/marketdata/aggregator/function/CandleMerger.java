package com.rmrx.marketnexus.marketdata.aggregator.function;

import com.rmrx.marketnexus.marketdata.aggregator.model.CandleUpdate;
import com.rmrx.marketnexus.marketdata.aggregator.model.Timeframe;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Builds a parent candle from its children. */
final class CandleMerger {

    private CandleMerger() {
    }

    /**
     * Merges children (sorted by openTime, non-empty) into a candle of {@code timeframe} starting at
     * {@code windowStart}. Only children with volume &gt; 0 contribute prices (Nobitex filler candles have v=0);
     * if all have v=0 the candle is flat at the first child's close (fillers carry the previous close).
     */
    static CandleUpdate merge(Timeframe timeframe, long windowStart, List<CandleUpdate> children, boolean closed) {
        CandleUpdate first = children.getFirst();
        BigDecimal open = null, high = null, low = null, close = null;
        BigDecimal volume = BigDecimal.ZERO;
        boolean reconciled = false;
        for (CandleUpdate child : children) {
            volume = volume.add(child.volume());
            reconciled |= child.reconciled();
            if (child.volume().signum() <= 0) {
                continue;
            }
            if (open == null) {
                open = child.open();
                high = child.high();
                low = child.low();
            } else {
                high = high.max(child.high());
                low = low.min(child.low());
            }
            close = child.close();
        }
        if (open == null) {
            open = high = low = close = first.close();
        }
        return new CandleUpdate(first.exchange(), first.symbol(), timeframe, Instant.ofEpochMilli(windowStart),
                open, high, low, close, volume, reconciled, closed);
    }
}
