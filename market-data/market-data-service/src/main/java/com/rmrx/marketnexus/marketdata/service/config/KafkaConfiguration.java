package com.rmrx.marketnexus.marketdata.service.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

/** In kafka mode: creates the service's topics on startup (Spring's {@link KafkaAdmin} picks up the beans). */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = CandleKafkaProperties.MODE_PROPERTY, havingValue = "kafka")
public class KafkaConfiguration {

    @Bean
    KafkaAdmin.NewTopics candleTopics(CandleKafkaProperties properties) {
        CandleKafkaProperties.Topics topics = properties.topics();
        return new KafkaAdmin.NewTopics(
                topic(topics.m1Live(), topics),
                topic(topics.m1Reconciled(), topics),
                topic(topics.h1Reconciled(), topics),
                topic(topics.candlesDerived(), topics),
                topic(topics.candleSeriesTracking(), topics));
    }

    private static NewTopic topic(String name, CandleKafkaProperties.Topics topics) {
        return TopicBuilder.name(name).partitions(topics.partitions()).replicas(topics.replicas()).build();
    }
}
