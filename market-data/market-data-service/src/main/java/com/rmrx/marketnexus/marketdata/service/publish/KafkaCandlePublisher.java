package com.rmrx.marketnexus.marketdata.service.publish;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import com.rmrx.marketnexus.marketdata.service.config.IngestionProperties;
import com.rmrx.marketnexus.marketdata.model.Candle;
import com.rmrx.marketnexus.marketdata.model.CandleJson;

/**
 * {@link CandlePublisher} on Kafka. Key = market ({@code exchange:symbol}), so all candles of one market land in the
 * same partition and stay in order; value = {@link CandleJson}.
 */
@Component
@ConditionalOnProperty(name = "market-data.ingestion.publisher", havingValue = "kafka")
public class KafkaCandlePublisher implements CandlePublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaCandlePublisher.class);

    private final KafkaTemplate<String, byte[]> kafka;
    private final String topic;

    public KafkaCandlePublisher(KafkaTemplate<String, byte[]> kafka, IngestionProperties properties) {
        this.kafka = kafka;
        this.topic = properties.topic();
    }

    @Override
    public void publish(Candle candle) {
        try {
            kafka.send(topic, candle.marketKey(), CandleJson.toBytes(candle))
                    .whenComplete((result, error) -> {
                        if (error != null) {
                            log.warn("Failed to publish {} {} {} to {}", candle.symbol(), candle.timeframe(),
                                    candle.openTime(), topic, error);
                        }
                    });
        } catch (RuntimeException e) {
            log.warn("Failed to publish {} {} {} to {}", candle.symbol(), candle.timeframe(), candle.openTime(), topic, e);
        }
    }
}
