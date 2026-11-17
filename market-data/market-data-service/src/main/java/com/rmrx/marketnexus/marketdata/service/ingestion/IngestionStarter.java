package com.rmrx.marketnexus.marketdata.service.ingestion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import com.rmrx.marketnexus.exchange.client.ExchangeStreamClient;
import com.rmrx.marketnexus.exchange.model.ExchangeSymbol;
import com.rmrx.marketnexus.exchange.model.ExchangeTimeframe;
import com.rmrx.marketnexus.marketdata.service.config.IngestionProperties;

/** Once the app is up (database migrated, Kafka configured), subscribes M1 for every configured symbol and connects. */
@Component
public class IngestionStarter {

    private static final Logger log = LoggerFactory.getLogger(IngestionStarter.class);

    private final ExchangeStreamClient stream;
    private final M1IngestionService ingestion;
    private final IngestionProperties properties;

    public IngestionStarter(ExchangeStreamClient stream, M1IngestionService ingestion, IngestionProperties properties) {
        this.stream = stream;
        this.ingestion = ingestion;
        this.properties = properties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (properties.symbols().isEmpty()) {
            log.warn("No symbols configured (market-data.ingestion.symbols); nothing to ingest");
            return;
        }
        for (String symbol : properties.symbols()) {
            stream.subscribeCandles(parse(symbol), ExchangeTimeframe.M1, ingestion);
        }
        stream.connect();
        log.info("Ingesting M1 from {} for {}", stream.exchangeType(), properties.symbols());
    }

    /** {@code BTC/USDT} → base BTC, quote USDT. */
    private static ExchangeSymbol parse(String symbol) {
        String[] parts = symbol.split("/");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Symbol must be BASE/QUOTE, got: " + symbol);
        }
        return ExchangeSymbol.of(parts[0], parts[1]);
    }
}
