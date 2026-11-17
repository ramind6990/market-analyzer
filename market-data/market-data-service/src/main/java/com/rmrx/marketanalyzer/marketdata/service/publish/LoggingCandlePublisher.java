package com.rmrx.marketanalyzer.marketdata.service.publish;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import com.rmrx.marketanalyzer.marketdata.model.Candle;
import com.rmrx.marketanalyzer.marketdata.model.CandleJson;
import com.rmrx.marketanalyzer.marketdata.service.config.IngestionProperties;

import java.nio.charset.StandardCharsets;

/**
 * Stand-in for Kafka while no broker is available: logs what would be sent (topic, key, JSON value) and sends nothing.
 * Active when {@code market-data.ingestion.publisher=logging} (the default for now).
 */
@Component
@ConditionalOnProperty(name = "market-data.ingestion.publisher", havingValue = "logging", matchIfMissing = true)
public class LoggingCandlePublisher implements CandlePublisher {

    private static final Logger log = LoggerFactory.getLogger(LoggingCandlePublisher.class);

    private final String topic;

    public LoggingCandlePublisher(IngestionProperties properties) {
        this.topic = properties.topic();
    }

    @Override
    public void publish(Candle candle) {
        log.info("[mock kafka] topic={} key={} value={}", topic, candle.marketKey(),
                new String(CandleJson.toBytes(candle), StandardCharsets.UTF_8));
    }
}
