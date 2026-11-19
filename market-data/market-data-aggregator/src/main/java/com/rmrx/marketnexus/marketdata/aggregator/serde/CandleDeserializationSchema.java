package com.rmrx.marketnexus.marketdata.aggregator.serde;

import com.rmrx.marketnexus.marketdata.aggregator.model.Candle;
import org.apache.flink.api.common.serialization.AbstractDeserializationSchema;

/** Reads input {@link Candle}s from Kafka message values. */
public class CandleDeserializationSchema extends AbstractDeserializationSchema<Candle> {

    @Override
    public Candle deserialize(byte[] message) {
        return CandleJson.MAPPER.readValue(message, Candle.class);
    }
}
