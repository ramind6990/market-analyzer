package com.rmrx.marketnexus.marketdata.service;

import com.rmrx.marketnexus.marketdata.service.ingestion.IngestionStarter;
import com.rmrx.marketnexus.marketdata.service.reconciliation.job.registration.ReconciliationRegistrar;
import com.rmrx.marketnexus.marketdata.service.reconciliation.job.execution.ReconciliationWorker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Market-data service: ingests live candles from exchanges, stores them, publishes them and reconciles gaps
 * (later also subscription management and a REST API for analysis).
 * <p>
 * Startup order, once the context is loaded: first one reconciliation registration run (so gaps up to now are known
 * and registered before live data arrives), then the reconciliation worker, then the live stream ingestion.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class MarketDataServiceApplication implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MarketDataServiceApplication.class);

    private final ReconciliationRegistrar registrar;
    private final ReconciliationWorker reconciliationWorker;
    private final IngestionStarter ingestionStarter;

    public MarketDataServiceApplication(ReconciliationRegistrar registrar, ReconciliationWorker reconciliationWorker,
                                        IngestionStarter ingestionStarter) {
        this.registrar = registrar;
        this.reconciliationWorker = reconciliationWorker;
        this.ingestionStarter = ingestionStarter;
    }

    public static void main(String[] args) {
        SpringApplication.run(MarketDataServiceApplication.class, args);
    }

    /** Startup order: register gaps up to now → start executing jobs → start the live stream. */
    @Override
    public void run(ApplicationArguments args) {
//        try {
//            registrar.registerAll();
//        } catch (RuntimeException e) {
//            log.error("Startup reconciliation registration failed; continuing", e);
//        }
        reconciliationWorker.start();
      //  ingestionStarter.start();
    }
}
