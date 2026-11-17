package com.rmrx.marketnexus.marketdata.model;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * JSON wire format of {@link Candle} (Kafka message values). Prices and volume are written as exact decimal numbers,
 * times as ISO-8601 strings.
 */
public final class CandleJson {

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private CandleJson() {
    }

    public static byte[] toBytes(Candle candle) {
        return MAPPER.writeValueAsBytes(candle);
    }

    public static Candle fromBytes(byte[] json) {
        return MAPPER.readValue(json, Candle.class);
    }
}
