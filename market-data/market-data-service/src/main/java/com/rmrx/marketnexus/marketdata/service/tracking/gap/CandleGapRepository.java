package com.rmrx.marketnexus.marketdata.service.tracking.gap;

import com.rmrx.marketnexus.marketdata.service.tracking.tracker.SeriesKey;
import com.rmrx.marketnexus.marketdata.model.Timeframe;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Storage of {@link CandleGap} rows. */
public interface CandleGapRepository {

    /**
     * Records a gap and returns the stored row. An unfilled gap with the same series and {@code from} already stored
     * (redelivery, restart) only widens its {@code to}.
     *
     * @return the stored row; empty if a gap with the same {@code from} is already filled (left untouched)
     */
    Optional<CandleGap> record(SeriesKey series, Instant from, Instant to, Instant detectedAt);

    /** Unfilled gaps of one exchange and timeframe, ordered by symbol and {@code from}. */
    List<CandleGap> findUnfilled(String exchange, Timeframe timeframe);

    /** Unfilled gaps of one series, ordered by {@code from}. */
    List<CandleGap> findUnfilled(SeriesKey series);

    /** Marks a gap filled. */
    void markFilled(long id, Instant filledAt);
}
