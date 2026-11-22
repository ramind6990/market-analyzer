package com.rmrx.marketnexus.marketanalysis.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param threads     worker threads computing series jobs; {@code 0} = number of CPU cores
 * @param initialBars rows produced by the first (unseeded) computation of a series, after its warmup
 * @param maxNewBars  bars one seeded job computes at most; the rest follows on the next trigger
 */
@ConfigurationProperties("market-analysis.job")
public record AnalysisJobProperties(int threads, int initialBars, int maxNewBars) {

    public AnalysisJobProperties {
        if (threads <= 0) {
            threads = Runtime.getRuntime().availableProcessors();
        }
        if (initialBars <= 0) {
            initialBars = 1000;
        }
        if (maxNewBars <= 0) {
            maxNewBars = 5000;
        }
    }
}
