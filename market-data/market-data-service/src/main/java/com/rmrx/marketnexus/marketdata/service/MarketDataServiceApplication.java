package com.rmrx.marketnexus.marketdata.service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Market-data service: ingests live candles from exchanges, stores them and publishes them
 * (later also reconciliation, subscription management and a REST API for analysis).
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class MarketDataServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MarketDataServiceApplication.class, args);
    }
}
