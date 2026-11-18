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
 * @param fetchChunkCandles     max candles fetched per job execution (one REST call; Nobitex: 500)
 * @param maxAttempts           failed executions of a job before it is marked FAILED
 * @param retryBackoff          wait before retrying a failed execution; doubles per attempt, capped at 1 hour
 * @param recentWindow          job windows ending within this of now get HIGH priority (recent gaps first)
 * @param worker                job worker settings
 * @param timeframes            timeframes to reconcile, with their settings; only listed timeframes are reconciled
 */
@ConfigurationProperties("market-data.reconciliation")
public record ReconciliationProperties(
        Duration retentionSafetyMargin,
        Integer maxWindowCandles,
        Duration scheduleInterval,
        Integer fetchChunkCandles,
        Integer maxAttempts,
        Duration retryBackoff,
        Duration recentWindow,
        Worker worker,
        Map<Timeframe, TimeframeSettings> timeframes) {

    private static final Duration MAX_RETRY_BACKOFF = Duration.ofHours(1);

    public ReconciliationProperties {
        retentionSafetyMargin = retentionSafetyMargin == null ? Duration.ofHours(1) : retentionSafetyMargin;
        maxWindowCandles = maxWindowCandles == null ? 500 : maxWindowCandles;
        scheduleInterval = scheduleInterval == null ? Duration.ofMinutes(5) : scheduleInterval;
        fetchChunkCandles = fetchChunkCandles == null ? 500 : fetchChunkCandles;
        maxAttempts = maxAttempts == null ? 5 : maxAttempts;
        retryBackoff = retryBackoff == null ? Duration.ofMinutes(1) : retryBackoff;
        recentWindow = recentWindow == null ? Duration.ofHours(1) : recentWindow;
        worker = worker == null ? new Worker(null, null, null, null) : worker;
        timeframes = timeframes == null || timeframes.isEmpty()
                ? Map.of(Timeframe.M1, new TimeframeSettings(Duration.ofDays(7), null))
                : new EnumMap<>(timeframes);
        if (maxWindowCandles < 1 || fetchChunkCandles < 1 || maxAttempts < 1) {
            throw new IllegalArgumentException("market-data.reconciliation: max-window-candles, fetch-chunk-candles "
                    + "and max-attempts must be >= 1");
        }
        for (Map.Entry<Timeframe, TimeframeSettings> entry : timeframes.entrySet()) {
            if (!retentionSafetyMargin.minus(entry.getValue().retention()).isNegative()) {
                throw new IllegalArgumentException("market-data.reconciliation: retention-safety-margin must be "
                        + "smaller than the retention of " + entry.getKey());
            }
        }
    }

    /**
     * Job worker ({@code market-data.reconciliation.worker.*}).
     *
     * @param threads           concurrent executions; the shared rate limit, not threads, bounds throughput (default 3)
     * @param claimBatchSize    jobs claimed from the database at once when the in-memory queue runs low (default 10,
     *                          at most requests-per-minute); small batches keep newly registered high-priority jobs fast
     * @param idlePollInterval  sleep when no job is due (default 5 s)
     * @param requestsPerMinute shared budget of job executions (= REST calls) per minute (default 50; Nobitex ~60/min)
     */
    public record Worker(Integer threads, Integer claimBatchSize, Duration idlePollInterval, Integer requestsPerMinute) {

        public Worker {
            threads = threads == null ? 3 : threads;
            requestsPerMinute = requestsPerMinute == null ? 50 : requestsPerMinute;
            claimBatchSize = claimBatchSize == null ? Math.min(10, requestsPerMinute) : claimBatchSize;
            idlePollInterval = idlePollInterval == null ? Duration.ofSeconds(5) : idlePollInterval;
            if (threads < 1 || requestsPerMinute < 1 || claimBatchSize < 1 || claimBatchSize > requestsPerMinute) {
                throw new IllegalArgumentException("market-data.reconciliation.worker: threads, claim-batch-size and "
                        + "requests-per-minute must be >= 1, and claim-batch-size <= requests-per-minute");
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

    /** Wait before the next attempt after {@code attempts} failed executions: backoff · 2^(attempts−1), at most 1 hour. */
    public Duration retryDelay(int attempts) {
        Duration delay = retryBackoff.multipliedBy(1L << Math.min(Math.max(attempts - 1, 0), 20));
        return delay.compareTo(MAX_RETRY_BACKOFF) > 0 ? MAX_RETRY_BACKOFF : delay;
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
