package com.rmrx.marketnexus.marketdata.aggregator.serde;

import com.rmrx.marketnexus.marketdata.aggregator.model.CandleUpdate;
import org.apache.flink.api.common.serialization.SerializationSchema;

import java.nio.charset.StandardCharsets;

/** Writes {@link CandleUpdate}s to Kafka: value = JSON, key = series key ({@code exchange:symbol:timeframe}). */
public final class CandleUpdateSerialization {

    private CandleUpdateSerialization() {
    }

    public static SerializationSchema<CandleUpdate> value() {
        return update -> CandleJson.MAPPER.writeValueAsBytes(update);
    }

    public static SerializationSchema<CandleUpdate> key() {
        return update -> update.seriesKey().getBytes(StandardCharsets.UTF_8);
    }
}
