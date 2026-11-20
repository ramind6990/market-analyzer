package com.rmrx.marketnexus.marketanalysis;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Market-analysis service: reacts to candle series changes published by market-data-service
 * (topic {@code candle-series-tracking}); analysis itself comes later.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class MarketAnalysisApplication {

    public static void main(String[] args) {
        SpringApplication.run(MarketAnalysisApplication.class, args);
    }
}
