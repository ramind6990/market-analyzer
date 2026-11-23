package com.rmrx.marketanalyzer.marketdata.aggregator.serde;

import com.rmrx.marketanalyzer.marketdata.aggregator.model.CandleUpdate;
import org.apache.flink.api.common.serialization.SerializationSchema;

import java.nio.charset.StandardCharsets;

/**
 * Writes {@link CandleUpdate}s to Kafka: value = JSON, key = series key ({@code exchange:symbol:timeframe}).
 * <p>
 * Named classes, not lambdas: the Kafka sink extracts the element type from the schema's generic interface
 * (lineage), which fails for lambdas.
 */
public final class CandleUpdateSerialization {

    private CandleUpdateSerialization() {
    }

    public static SerializationSchema<CandleUpdate> value() {
        return new ValueSchema();
    }

    public static SerializationSchema<CandleUpdate> key() {
        return new KeySchema();
    }

    /** Message value: the update as JSON. */
    public static final class ValueSchema implements SerializationSchema<CandleUpdate> {

        @Override
        public byte[] serialize(CandleUpdate update) {
            return CandleJson.MAPPER.writeValueAsBytes(update);
        }
    }

    /** Message key: the series key, so all updates of one series share a partition and stay in order. */
    public static final class KeySchema implements SerializationSchema<CandleUpdate> {

        @Override
        public byte[] serialize(CandleUpdate update) {
            return update.seriesKey().getBytes(StandardCharsets.UTF_8);
        }
    }
}
