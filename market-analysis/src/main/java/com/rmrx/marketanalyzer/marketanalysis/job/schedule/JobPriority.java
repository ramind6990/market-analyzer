package com.rmrx.marketanalyzer.marketanalysis.job.schedule;

import com.rmrx.marketanalyzer.marketanalysis.job.SeriesTrigger;

import java.time.Instant;
import java.util.Comparator;

/**
 * Order of queued jobs: LIVE before BACKFILL, then earliest deadline first (bar close + one timeframe), then smaller
 * timeframe, then arrival.
 * <p>
 * Earliest deadline instead of strict "smaller timeframe first": in a burst at 10:00 the M1 bar is due 10:01, M15
 * 10:15, H1 11:00, so M1 < M5 < M15 < H1 < H4 anyway; but under sustained M1 load an H1 job still runs once its
 * deadline is the earliest, instead of starving.
 */
record JobPriority(boolean live, Instant deadline, int timeframeOrder, long sequence)
        implements Comparable<JobPriority> {

    private static final Comparator<JobPriority> ORDER = Comparator
            .comparing((JobPriority p) -> !p.live)
            .thenComparing(JobPriority::deadline)
            .thenComparingInt(JobPriority::timeframeOrder)
            .thenComparingLong(JobPriority::sequence);

    static JobPriority of(SeriesTrigger trigger, Instant now, long sequence) {
        return new JobPriority(trigger.isLive(now), trigger.deadline(), trigger.series().timeframe().ordinal(),
                sequence);
    }

    @Override
    public int compareTo(JobPriority other) {
        return ORDER.compare(this, other);
    }
}
