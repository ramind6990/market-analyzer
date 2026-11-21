package com.rmrx.marketnexus.marketanalysis.technical.indicator.preset;

import com.rmrx.marketnexus.marketanalysis.technical.indicator.spec.IndicatorSpec;
import com.rmrx.marketnexus.marketdata.model.Timeframe;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/** A named, ready-to-use spec with conventional parameters, applied to the given timeframes. */
public record IndicatorPreset(String name, IndicatorSpec spec, Set<Timeframe> timeframes) {

    public IndicatorPreset {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(spec, "spec");
        timeframes = Set.copyOf(EnumSet.copyOf(timeframes));
    }

    public boolean appliesTo(Timeframe timeframe) {
        return timeframes.contains(timeframe);
    }
}
