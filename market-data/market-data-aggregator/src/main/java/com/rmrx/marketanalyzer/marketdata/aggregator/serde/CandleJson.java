package com.rmrx.marketanalyzer.marketdata.aggregator.serde;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** JSON wire format of the aggregator's Kafka messages: exact decimals, ISO-8601 times. */
final class CandleJson {

    static final ObjectMapper MAPPER = JsonMapper.builder()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private CandleJson() {
    }
}
