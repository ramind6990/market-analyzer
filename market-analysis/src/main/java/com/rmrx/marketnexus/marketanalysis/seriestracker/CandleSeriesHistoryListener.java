package com.rmrx.marketnexus.marketanalysis.seriestracker;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Receives {@link CandleSeriesHistoryChanged} messages from market-data-service
 * ({@code market-analysis.kafka.topics.candle-series-tracking}).
 * <p>
 * LOGGING ONLY FOR NOW: later this decides whether a series has enough complete history ({@code minHistory}) and
 * triggers analysis. Unreadable messages are logged and skipped.
 */
@Component
public class CandleSeriesHistoryListener {

    private static final Logger log = LoggerFactory.getLogger(CandleSeriesHistoryListener.class);

    @KafkaListener(topics = "${market-analysis.kafka.topics.candle-series-tracking:candle-series-tracking}")
    public void onMessage(ConsumerRecord<String, byte[]> record) {
        CandleSeriesHistoryChanged change;
        try {
            change = CandleSeriesHistoryChangedJson.fromBytes(record.value());
        } catch (RuntimeException e) {
            log.error("Skipping unreadable message on {} (partition {}, offset {}, key {})", record.topic(),
                    record.partition(), record.offset(), record.key(), e);
            return;
        }
        log.info("Series {}: {} complete candle(s) up to {}", change.seriesKey(), change.newestRunLength(),
                change.newestOpenTime());
    }
}
