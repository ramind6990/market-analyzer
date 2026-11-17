package com.rmrx.marketnexus.marketdata.service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.rmrx.marketnexus.exchange.client.ExchangeClients;
import com.rmrx.marketnexus.exchange.client.ExchangeStreamClient;
import com.rmrx.marketnexus.exchange.config.ExchangeConfig;

/** Builds the exchange contracts from the app's properties; the exchange module itself reads no configuration. */
@Configuration(proxyBeanMethods = false)
public class IngestionConfiguration {

    @Bean
    ExchangeConfig exchangeConfig(IngestionProperties properties) {
        return new ExchangeConfig(properties.exchangeType(), properties.baseUrl(), properties.userAgent())
                .withWebsocketUrl(properties.websocketUrl());
    }

    /** Real exchange WebSocket, or a generated stream ({@code market-data.ingestion.stream: live | mock | mock-gaps}). */
    @Bean(destroyMethod = "close")
    ExchangeStreamClient exchangeStreamClient(ExchangeConfig exchangeConfig, IngestionProperties properties) {
        return switch (properties.stream()) {
            case LIVE -> ExchangeClients.createStream(exchangeConfig);
            case MOCK -> ExchangeClients.createMockStream(properties.exchangeType(),
                    properties.mockStream().emitInterval(), properties.mockStream().seed());
            case MOCK_GAPS -> ExchangeClients.createGapMockStream(properties.exchangeType(),
                    properties.mockStream().seed());
        };
    }
}
