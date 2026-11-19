package com.rmrx.marketnexus.marketdata.service.reconciliation.job.execution;

import com.rmrx.marketnexus.marketdata.model.Candle;
import com.rmrx.marketnexus.marketdata.model.Timeframe;
import com.rmrx.marketnexus.marketdata.service.candle.UpsertedCandle;
import com.rmrx.marketnexus.marketdata.service.config.CandleKafkaProperties;
import com.rmrx.marketnexus.marketdata.service.publish.CandlePublisher;
import com.rmrx.marketnexus.marketdata.service.reconciliation.job.ReconciliationJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * Publishes candles written by reconciliation (inserted or changed, after the commit) to the Flink candle aggregator.
 * The aggregator has two separate entry points, so the topic depends on the timeframe:
 * <ul>
 *   <li>M1 → {@code market-data.kafka.topics.m1-reconciled} (feeds the cascade up to M30);</li>
 *   <li>H1 → {@code market-data.kafka.topics.h1-reconciled} (authoritative H1, completes the hour in the H1 stage).</li>
 * </ul>
 * Other timeframes have no aggregator input and are not published. Candles go out in open-time order.
 */
@Component
public class ReconciledCandlePublisher {

    private static final Logger log = LoggerFactory.getLogger(ReconciledCandlePublisher.class);

    private final CandlePublisher publisher;
    private final CandleKafkaProperties.Topics topics;

    public ReconciledCandlePublisher(CandlePublisher publisher, CandleKafkaProperties kafka) {
        this.publisher = publisher;
        this.topics = kafka.topics();
    }

    public void publish(ReconciliationJob job, List<UpsertedCandle> candles) {
        if (candles.isEmpty()) {
            return;
        }
        String topic = topicFor(job.timeframe());
        if (topic == null) {
            log.warn("{}: {} reconciled {} candle(s) not published, the aggregator takes only M1 and H1", job,
                    candles.size(), job.timeframe());
            return;
        }
        candles.stream()
                .map(UpsertedCandle::candle)
                .sorted(Comparator.comparing(Candle::openTime))
                .forEach(candle -> publisher.publish(topic, candle));
        log.debug("{}: published {} reconciled candle(s) to {}", job, candles.size(), topic);
    }

    private String topicFor(Timeframe timeframe) {
        return switch (timeframe) {
            case M1 -> topics.m1Reconciled();
            case H1 -> topics.h1Reconciled();
            default -> null;
        };
    }
}
