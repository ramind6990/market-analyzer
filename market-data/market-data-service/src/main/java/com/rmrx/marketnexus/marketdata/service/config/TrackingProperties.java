package com.rmrx.marketnexus.marketdata.service.config;

import com.rmrx.marketnexus.marketdata.model.Timeframe;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;

/**
 * Settings of series tracking ({@code market-data.tracking.*} in application.yml).
 *
 * @param rebuildCandles how far back the book is rebuilt at startup, in candles of each timeframe (default 1000);
 *                       reaches further back when an unfilled gap is older, so it can still be settled
 * @param timeframes     timeframes tracked per symbol (default: all)
 */
@ConfigurationProperties("market-data.tracking")
public record TrackingProperties(Integer rebuildCandles, List<Timeframe> timeframes) {

    public TrackingProperties {
        rebuildCandles = rebuildCandles == null ? 1000 : rebuildCandles;
        timeframes = timeframes == null || timeframes.isEmpty()
                ? Arrays.asList(Timeframe.values())
                : List.copyOf(EnumSet.copyOf(timeframes));
        if (rebuildCandles < 1) {
            throw new IllegalArgumentException("market-data.tracking.rebuild-candles must be >= 1");
        }
    }
}
