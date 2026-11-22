package com.rmrx.marketnexus.marketanalysis.config;

import com.rmrx.marketnexus.marketanalysis.technical.indicator.catalog.DefaultIndicatorCatalog;
import com.rmrx.marketnexus.marketanalysis.technical.indicator.catalog.IndicatorCatalog;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

import java.time.Clock;

@Configuration
public class AnalysisConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /** All indicator types known to the code. */
    @Bean
    public IndicatorCatalog indicatorCatalog() {
        return DefaultIndicatorCatalog.create();
    }

    /** Topics this service produces (created on startup by Spring's {@link KafkaAdmin}). */
    @Bean
    public KafkaAdmin.NewTopics analysisTopics(
            @Value("${market-analysis.kafka.topics.series-indicators-updated}") String seriesIndicatorsUpdated,
            @Value("${market-analysis.kafka.topics.partitions}") int partitions,
            @Value("${market-analysis.kafka.topics.replicas}") int replicas) {
        return new KafkaAdmin.NewTopics(
                TopicBuilder.name(seriesIndicatorsUpdated).partitions(partitions).replicas(replicas).build());
    }
}
