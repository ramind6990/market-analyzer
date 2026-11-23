package com.rmrx.marketanalyzer.marketdata.service.reconciliation.job.execution;

import com.rmrx.marketanalyzer.marketdata.service.config.ReconciliationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Shared budget of job executions (= REST calls to the exchange): at most {@code requests-per-minute}, evenly spaced
 * ({@code 60 s / rpm} apart). Shared by all worker threads; unused time does not accumulate into bursts.
 */
@Component
public class ReconciliationRateLimiter {

    private final long spacingNanos;
    private long nextSlotNanos = System.nanoTime();

    public ReconciliationRateLimiter(ReconciliationProperties properties) {
        this.spacingNanos = Duration.ofMinutes(1).toNanos() / properties.worker().requestsPerMinute();
    }

    /** Blocks until the caller may make its next request. */
    public void acquire() throws InterruptedException {
        long waitNanos;
        synchronized (this) {
            long now = System.nanoTime();
            long slot = Math.max(now, nextSlotNanos);
            nextSlotNanos = slot + spacingNanos;
            waitNanos = slot - now;
        }
        if (waitNanos > 0) {
            Thread.sleep(Duration.ofNanos(waitNanos));
        }
    }
}
