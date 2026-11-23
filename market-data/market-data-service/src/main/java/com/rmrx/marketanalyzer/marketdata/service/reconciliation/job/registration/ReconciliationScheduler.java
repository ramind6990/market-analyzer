package com.rmrx.marketanalyzer.marketdata.service.reconciliation.job.registration;

import com.rmrx.marketanalyzer.marketdata.service.config.ReconciliationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.FixedDelayTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

/**
 * Runs {@link ReconciliationRegistrar#registerAll()} periodically, every {@code market-data.reconciliation.schedule-interval}
 * after the previous run finished (fixed delay: runs never overlap). The first periodic run comes one interval after
 * startup; the startup run itself is triggered by the application.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class ReconciliationScheduler implements SchedulingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationScheduler.class);

    private final ReconciliationRegistrar registrar;
    private final ReconciliationProperties properties;

    public ReconciliationScheduler(ReconciliationRegistrar registrar, ReconciliationProperties properties) {
        this.registrar = registrar;
        this.properties = properties;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.addFixedDelayTask(new FixedDelayTask(this::runRegistration,
                properties.scheduleInterval(), properties.scheduleInterval()));
        log.info("Reconciliation registration scheduled every {}", properties.scheduleInterval());
    }

    private void runRegistration() {
        try {
            registrar.registerAll();
        } catch (RuntimeException e) {
            log.error("Scheduled reconciliation registration failed", e);
        }
    }
}
