package com.rmrx.marketnexus.marketanalysis.job.publish;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Publishes {@link SeriesIndicatorsUpdated} (JSON, times ISO-8601) after the rows are stored. Key = series, so the
 * messages of one series stay in order. Asynchronous; failures are only logged (values are in the DB anyway).
 */
@Component
public class SeriesIndicatorsPublisher {

    private static final Logger log = LoggerFactory.getLogger(SeriesIndicatorsPublisher.class);
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private final KafkaTemplate<String, byte[]> kafka;
    private final String topic;

    public SeriesIndicatorsPublisher(KafkaTemplate<String, byte[]> kafka,
                                     @Value("${market-analysis.kafka.topics.series-indicators-updated}") String topic) {
        this.kafka = kafka;
        this.topic = topic;
    }

    public void publish(SeriesIndicatorsUpdated update) {
        // LOGGING ONLY FOR NOW: Kafka publishing disabled until a consumer (strategy) exists.
        log.info("Indicators updated: {}", update);
//        try {
//            kafka.send(topic, update.seriesKey(), MAPPER.writeValueAsBytes(update))
//                    .whenComplete((result, error) -> {
//                        if (error != null) {
//                            log.warn("Failed to publish {} to {}", update, topic, error);
//                        }
//                    });
//        } catch (RuntimeException e) {
//            log.warn("Failed to publish {} to {}", update, topic, e);
//        }
    }
}
