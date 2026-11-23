package com.rmrx.marketnexus.marketdata.service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.rmrx.marketnexus.exchange.client.ExchangeClient;
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

    /** Exchange REST client (reconciliation fetches closed candles with it). */
    @Bean(destroyMethod = "close")
    ExchangeClient exchangeClient(ExchangeConfig exchangeConfig) {
        return ExchangeClients.create(exchangeConfig);
    }

    /** Exchange WebSocket (live M1 ingestion). */
    @Bean(destroyMethod = "close")
    ExchangeStreamClient exchangeStreamClient(ExchangeConfig exchangeConfig) {
        return ExchangeClients.createStream(exchangeConfig);
    }
}
