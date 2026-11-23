package com.rmrx.marketanalyzer.marketdata.service.publish;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import com.rmrx.marketanalyzer.marketdata.model.Candle;
import com.rmrx.marketanalyzer.marketdata.model.CandleJson;
import com.rmrx.marketanalyzer.marketdata.service.config.CandleKafkaProperties;

import java.nio.charset.StandardCharsets;

/**
 * Stand-in for Kafka while no broker is available: logs what would be sent (topic, key, JSON value) and sends nothing.
 * Active when {@code market-data.kafka.mode=logging} (the default).
 */
@Component
@ConditionalOnProperty(name = CandleKafkaProperties.MODE_PROPERTY, havingValue = "logging", matchIfMissing = true)
public class LoggingCandlePublisher implements CandlePublisher {

    private static final Logger log = LoggerFactory.getLogger(LoggingCandlePublisher.class);

    @Override
    public void publish(String topic, Candle candle) {
        log.info("[mock kafka] topic={} key={} value={}", topic, candle.marketKey(),
                new String(CandleJson.toBytes(candle), StandardCharsets.UTF_8));
    }
}
