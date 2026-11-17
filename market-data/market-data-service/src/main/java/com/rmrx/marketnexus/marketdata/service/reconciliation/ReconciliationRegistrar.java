package com.rmrx.marketnexus.marketdata.service.reconciliation;

import com.rmrx.marketnexus.marketdata.model.Timeframe;
import com.rmrx.marketnexus.marketdata.service.config.IngestionProperties;
import com.rmrx.marketnexus.marketdata.service.config.ReconciliationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Turns M1 gaps into reconciliation jobs. One run, for all given symbols:
 * <ol>
 *   <li>load each series' watermark ({@code market_series});</li>
 *   <li>detect gaps after the watermarks (one query for all symbols);</li>
 *   <li>per symbol, in one transaction: clip gaps to the M1 retention horizon (older parts are unrecoverable and only
 *       logged), move the watermark to just before the first recoverable gap, pack gaps into fetch windows, drop what
 *       open jobs already cover, and register the rest as PENDING jobs.</li>
 * </ol>
 * Fetching the windows is done by a worker (next step).
 */
@Service
public class ReconciliationRegistrar {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationRegistrar.class);

    /** Outcome per symbol. */
    public record Registration(String symbol, Instant watermark, List<Gap> unrecoverable, List<FetchWindow> registered) {
    }

    private final GapDetectionService gapDetection;
    private final MarketSeriesRepository seriesRepository;
    private final ReconciliationJobRepository jobRepository;
    private final ReconciliationProperties reconciliation;
    private final IngestionProperties ingestion;
    private final TransactionTemplate transaction;
    private final Clock clock;

    @Autowired
    public ReconciliationRegistrar(GapDetectionService gapDetection, MarketSeriesRepository seriesRepository,
                                   ReconciliationJobRepository jobRepository, ReconciliationProperties reconciliation,
                                   IngestionProperties ingestion, PlatformTransactionManager transactionManager) {
        this(gapDetection, seriesRepository, jobRepository, reconciliation, ingestion,
                new TransactionTemplate(transactionManager), Clock.systemUTC());
    }

    ReconciliationRegistrar(GapDetectionService gapDetection, MarketSeriesRepository seriesRepository,
                            ReconciliationJobRepository jobRepository, ReconciliationProperties reconciliation,
                            IngestionProperties ingestion, TransactionTemplate transaction, Clock clock) {
        this.gapDetection = gapDetection;
        this.seriesRepository = seriesRepository;
        this.jobRepository = jobRepository;
        this.reconciliation = reconciliation;
        this.ingestion = ingestion;
        this.transaction = transaction;
        this.clock = clock;
    }

    /** Temporary trigger until a scheduler exists: one registration run for the configured symbols at startup. */
    @EventListener(ApplicationReadyEvent.class)
    public void registerAtStartup() {
        try {
            registerM1(ingestion.exchangeType().name(), ingestion.symbols());
        } catch (RuntimeException e) {
            log.error("Startup reconciliation registration failed", e);
        }
    }

    /** Detects M1 gaps for the symbols and registers jobs for them; returns what was done per symbol. */
    public List<Registration> registerM1(String exchange, List<String> symbols) {
        Timeframe timeframe = Timeframe.M1;
        if (symbols.isEmpty()) {
            return List.of();
        }
        Map<String, Instant> watermarks = seriesRepository.findWatermarks(exchange, timeframe, symbols);
        List<GapCheckResult> results = gapDetection.check(exchange, timeframe, watermarks);
        Map<String, List<FetchWindow>> openJobs = jobRepository.findOpenWindows(exchange, timeframe, symbols);
        Instant now = clock.instant();

        List<Registration> registrations = new ArrayList<>();
        for (GapCheckResult result : results) {
            Registration registration = transaction.execute(status -> register(exchange, timeframe, result,
                    watermarks.get(result.symbol()), openJobs.getOrDefault(result.symbol(), List.of()), now));
            registrations.add(registration);
            log(exchange, timeframe, result, registration);
        }
        return registrations;
    }

    private Registration register(String exchange, Timeframe timeframe, GapCheckResult result, Instant oldWatermark,
                                  List<FetchWindow> openWindows, Instant now) {
        if (result.checkedAfter() == null) {
            seriesRepository.updateWatermark(exchange, result.symbol(), timeframe, oldWatermark, now);
            return new Registration(result.symbol(), oldWatermark, List.of(), List.of());
        }
        Instant horizon = GapPlanner.horizon(now, reconciliation.effectiveM1Retention(), result.checkedAfter(), timeframe);
        GapPlanner.Clipped clipped = GapPlanner.clipToHorizon(result.gaps(), horizon, timeframe);
        Instant watermark = GapPlanner.newWatermark(oldWatermark, result, clipped.recoverable());
        List<FetchWindow> windows = GapPlanner.pack(clipped.recoverable(), reconciliation.maxWindowCandles(), timeframe);
        List<FetchWindow> toRegister = GapPlanner.subtract(windows, openWindows, timeframe);

        seriesRepository.updateWatermark(exchange, result.symbol(), timeframe, watermark, now);
        jobRepository.insertAll(exchange, result.symbol(), timeframe, toRegister);
        return new Registration(result.symbol(), watermark, clipped.unrecoverable(), toRegister);
    }

    private static void log(String exchange, Timeframe timeframe, GapCheckResult result, Registration registration) {
        String series = exchange + " " + result.symbol() + " " + timeframe;
        if (result.checkedAfter() == null) {
            log.info("Reconciliation {}: no candles stored", series);
            return;
        }
        log.info("Reconciliation {}: checked ({} .. {}], {} gap(s), watermark -> {}, {} job(s) registered",
                series, result.checkedAfter(), result.upTo(), result.gaps().size(), registration.watermark(),
                registration.registered().size());
        for (Gap gap : registration.unrecoverable()) {
            log.warn("  unrecoverable at {} (older than retention): {} ({} candle(s))", timeframe, gap,
                    gap.missingCandles(timeframe));
        }
        for (FetchWindow window : registration.registered()) {
            log.info("  job window {} ({} candle(s))", window, window.candles(timeframe));
        }
    }
}
