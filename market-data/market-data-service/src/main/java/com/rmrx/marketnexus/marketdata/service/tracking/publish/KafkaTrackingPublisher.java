package com.rmrx.marketnexus.marketdata.service.tracking.publish;

import com.rmrx.marketnexus.marketdata.service.tracking.tracker.CompleteHistory;
import com.rmrx.marketnexus.marketdata.service.tracking.tracker.SeriesKey;
import com.rmrx.marketnexus.marketdata.service.tracking.tracker.TrackingListener;
import com.rmrx.marketnexus.marketdata.service.config.CandleKafkaProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;


/**
 * Publishes every change of a series' complete history as {@link CandleSeriesHistoryChanged} to Kafka
 * ({@code market-data.kafka.topics.candle-series-tracking}), for analysis. Key = series ({@code exchange:symbol:timeframe}),
 * so the messages of one series stay in order.
 * <p>
 * Runs inside the series lock: the send is asynchronous and failures are only logged, never thrown.
 */
@Component
public class KafkaTrackingPublisher implements TrackingListener {

    private static final Logger log = LoggerFactory.getLogger(KafkaTrackingPublisher.class);

    private final KafkaTemplate<String, byte[]> kafka;
    private final String topic;

    public KafkaTrackingPublisher(KafkaTemplate<String, byte[]> kafka, CandleKafkaProperties properties) {
        this.kafka = kafka;
        this.topic = properties.topics().candleSeriesTracking();
    }

    @Override
    public void onCompleteHistoryChanged(SeriesKey series, CompleteHistory history) {
        CandleSeriesHistoryChanged change = CandleSeriesHistoryChanged.of(series, history);
        try {
            kafka.send(topic, change.seriesKey(), CandleSeriesHistoryChangedJson.toBytes(change))
                    .whenComplete((result, error) -> {
                        if (error != null) {
                            log.warn("Failed to publish {} to {}", change, topic, error);
                        }
                    });
        } catch (RuntimeException e) {
            log.warn("Failed to publish {} to {}", change, topic, e);
        }
    }
}
