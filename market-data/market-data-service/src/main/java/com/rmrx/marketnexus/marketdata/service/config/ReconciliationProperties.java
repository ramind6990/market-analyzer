package com.rmrx.marketnexus.marketdata.service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Settings of reconciliation ({@code market-data.reconciliation.*} in application.yml).
 *
 * @param m1Retention           how far back the exchange keeps M1 candles (Nobitex: 7 days); older gaps can't be fetched
 * @param retentionSafetyMargin subtracted from the retention, so a job doesn't expire while waiting in the queue
 * @param maxWindowCandles      max span (in candles) of one job window when packing nearby gaps together
 */
@ConfigurationProperties("market-data.reconciliation")
public record ReconciliationProperties(Duration m1Retention, Duration retentionSafetyMargin, Integer maxWindowCandles) {

    public ReconciliationProperties {
        m1Retention = m1Retention == null ? Duration.ofDays(7) : m1Retention;
        retentionSafetyMargin = retentionSafetyMargin == null ? Duration.ofHours(1) : retentionSafetyMargin;
        maxWindowCandles = maxWindowCandles == null ? 500 : maxWindowCandles;
        if (maxWindowCandles < 1 || !retentionSafetyMargin.minus(m1Retention).isNegative()) {
            throw new IllegalArgumentException("market-data.reconciliation: need max-window-candles >= 1 and "
                    + "retention-safety-margin < m1-retention");
        }
    }

    /** Usable M1 history: retention minus the safety margin. */
    public Duration effectiveM1Retention() {
        return m1Retention.minus(retentionSafetyMargin);
    }
}
