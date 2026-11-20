package com.rmrx.marketnexus.marketdata.service.reconciliation.series;

import com.rmrx.marketnexus.marketdata.service.config.ReconciliationProperties;
import com.rmrx.marketnexus.marketdata.service.tracking.tracker.SeriesKey;
import com.rmrx.marketnexus.marketdata.service.tracking.rebuild.TrackingLookback;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

/**
 * Asks the tracking rebuild to load reconciled series back to their watermark: the registrar treats everything
 * between the watermark and the oldest candle the book knows as missing, so the book must know all of it.
 */
@Component
public class WatermarkLookback implements TrackingLookback {

    private final MarketSeriesRepository seriesRepository;
    private final ReconciliationProperties reconciliation;

    public WatermarkLookback(MarketSeriesRepository seriesRepository, ReconciliationProperties reconciliation) {
        this.seriesRepository = seriesRepository;
        this.reconciliation = reconciliation;
    }

    @Override
    public Optional<Instant> rebuildFrom(SeriesKey series) {
        if (!reconciliation.reconciledTimeframes().contains(series.timeframe())) {
            return Optional.empty();
        }
        return seriesRepository.findWatermark(series.exchange(), series.symbol(), series.timeframe());
    }
}
