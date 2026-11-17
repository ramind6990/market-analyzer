package com.rmrx.marketnexus.marketdata.service.config;

import com.rmrx.marketnexus.marketdata.model.Timeframe;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Settings of reconciliation ({@code market-data.reconciliation.*} in application.yml).
 *
 * @param retentionSafetyMargin subtracted from each retention, so a job doesn't expire while waiting in the queue
 * @param maxWindowCandles      max span (in candles) of one job window when packing nearby gaps together
 * @param scheduleInterval      pause between two periodic registration runs
 * @param timeframes            timeframes to reconcile, with their settings; only listed timeframes are reconciled
 */
@ConfigurationProperties("market-data.reconciliation")
public record ReconciliationProperties(
        Duration retentionSafetyMargin,
        Integer maxWindowCandles,
        Duration scheduleInterval,
        Map<Timeframe, TimeframeSettings> timeframes) {

    public ReconciliationProperties {
        retentionSafetyMargin = retentionSafetyMargin == null ? Duration.ofHours(1) : retentionSafetyMargin;
        maxWindowCandles = maxWindowCandles == null ? 500 : maxWindowCandles;
        scheduleInterval = scheduleInterval == null ? Duration.ofMinutes(5) : scheduleInterval;
        timeframes = timeframes == null || timeframes.isEmpty()
                ? Map.of(Timeframe.M1, new TimeframeSettings(Duration.ofDays(7), null))
                : new EnumMap<>(timeframes);
        if (maxWindowCandles < 1) {
            throw new IllegalArgumentException("market-data.reconciliation.max-window-candles must be >= 1");
        }
        for (Map.Entry<Timeframe, TimeframeSettings> entry : timeframes.entrySet()) {
            if (!retentionSafetyMargin.minus(entry.getValue().retention()).isNegative()) {
                throw new IllegalArgumentException("market-data.reconciliation: retention-safety-margin must be "
                        + "smaller than the retention of " + entry.getKey());
            }
        }
    }

    /**
     * Per-timeframe settings ({@code market-data.reconciliation.timeframes.<TF>.*}).
     *
     * @param retention  how far back the exchange keeps this timeframe, or how far back to reconcile on a first run
     * @param gridOffset where candles of this timeframe start relative to round UTC boundaries (Nobitex H1: 30m)
     */
    public record TimeframeSettings(Duration retention, Duration gridOffset) {

        public TimeframeSettings {
            if (retention == null || retention.isNegative() || retention.isZero()) {
                throw new IllegalArgumentException("retention must be positive");
            }
            gridOffset = gridOffset == null ? Duration.ZERO : gridOffset;
        }
    }

    /** Configured timeframes, smallest first (M1 before H1). */
    public List<Timeframe> reconciledTimeframes() {
        return timeframes.keySet().stream().sorted().toList();
    }

    /** Usable history of a timeframe: its retention minus the safety margin. */
    public Duration effectiveRetention(Timeframe timeframe) {
        return settings(timeframe).retention().minus(retentionSafetyMargin);
    }

    /** An open time on the timeframe's candle grid (e.g. 1970-01-01T00:30Z for Nobitex H1), for alignment. */
    public Instant gridReference(Timeframe timeframe) {
        return Instant.EPOCH.plus(settings(timeframe).gridOffset());
    }

    private TimeframeSettings settings(Timeframe timeframe) {
        TimeframeSettings settings = timeframes.get(timeframe);
        if (settings == null) {
            throw new IllegalArgumentException("Timeframe " + timeframe + " is not configured for reconciliation");
        }
        return settings;
    }
}
