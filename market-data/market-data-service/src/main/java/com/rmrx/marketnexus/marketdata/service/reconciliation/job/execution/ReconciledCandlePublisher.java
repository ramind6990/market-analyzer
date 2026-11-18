package com.rmrx.marketnexus.marketdata.service.reconciliation.job.execution;

import com.rmrx.marketnexus.marketdata.service.reconciliation.job.ReconciliationJob;

import com.rmrx.marketnexus.marketdata.service.candle.UpsertedCandle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Publishes candles written by reconciliation (inserted or changed) to downstream consumers, e.g. Kafka topics
 * {@code m1-reconciled} / {@code h1-reconciled} for the Flink aggregator.
 * <p>
 * EMPTY FOR NOW: only logs how many candles would be published.
 */
@Component
public class ReconciledCandlePublisher {

    private static final Logger log = LoggerFactory.getLogger(ReconciledCandlePublisher.class);

    public void publish(ReconciliationJob job, List<UpsertedCandle> candles) {
        if (!candles.isEmpty()) {
            log.debug("{}: would publish {} reconciled candle(s) (not implemented yet)", job, candles.size());
        }
    }
}
