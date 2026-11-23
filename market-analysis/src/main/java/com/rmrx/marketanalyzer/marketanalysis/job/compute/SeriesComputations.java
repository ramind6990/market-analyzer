package com.rmrx.marketanalyzer.marketanalysis.job.compute;

import com.rmrx.marketanalyzer.marketanalysis.store.IndicatorDefinitionWriter;
import com.rmrx.marketanalyzer.marketanalysis.store.IndicatorLayoutWriter;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.catalog.IndicatorCatalog;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.compute.IndicatorComputation;
import com.rmrx.marketanalyzer.marketanalysis.technical.indicator.preset.IndicatorPresets;
import com.rmrx.marketanalyzer.marketdata.model.Timeframe;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Indicator set of a series = presets of its timeframe (v1). One resolved graph per timeframe, built once; its layout
 * (names of the stored values) is saved when built. On startup the definitions and the layouts of all timeframes are
 * saved, so the database describes the stored values before the first job runs.
 */
@Component
public class SeriesComputations {

    private final IndicatorCatalog catalog;
    private final IndicatorLayoutWriter layouts;
    private final IndicatorDefinitionWriter definitions;
    private final Map<Timeframe, IndicatorComputation> byTimeframe = new ConcurrentHashMap<>();

    public SeriesComputations(IndicatorCatalog catalog, IndicatorLayoutWriter layouts,
                              IndicatorDefinitionWriter definitions) {
        this.catalog = catalog;
        this.layouts = layouts;
        this.definitions = definitions;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void register() {
        definitions.saveAll(catalog.definitions());
        for (Timeframe timeframe : Timeframe.values()) {
            forTimeframe(timeframe);
        }
    }

    public IndicatorComputation forTimeframe(Timeframe timeframe) {
        return byTimeframe.computeIfAbsent(timeframe, tf -> {
            IndicatorComputation computation = new IndicatorComputation(catalog.resolve(IndicatorPresets.specsFor(tf)));
            layouts.save(computation.graph());
            return computation;
        });
    }
}
