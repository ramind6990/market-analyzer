package com.rmrx.marketnexus.marketdata.service.publish;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import com.rmrx.marketnexus.marketdata.model.Candle;
import com.rmrx.marketnexus.marketdata.model.CandleJson;
import com.rmrx.marketnexus.marketdata.service.config.CandleKafkaProperties;

/**
 * {@link CandlePublisher} on Kafka. Key = market ({@code exchange:symbol}), so all candles of one market land in the
 * same partition and stay in order; value = {@link CandleJson}. Active when {@code market-data.kafka.mode=kafka}.
 */
@Component
@ConditionalOnProperty(name = CandleKafkaProperties.MODE_PROPERTY, havingValue = "kafka")
public class KafkaCandlePublisher implements CandlePublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaCandlePublisher.class);

    private final KafkaTemplate<String, byte[]> kafka;

    public KafkaCandlePublisher(KafkaTemplate<String, byte[]> kafka) {
        this.kafka = kafka;
    }

    @Override
    public void publish(String topic, Candle candle) {
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
