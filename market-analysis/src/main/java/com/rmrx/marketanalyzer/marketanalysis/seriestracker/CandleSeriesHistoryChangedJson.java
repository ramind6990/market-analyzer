package com.rmrx.marketanalyzer.marketanalysis.seriestracker;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * JSON wire format of {@link CandleSeriesHistoryChanged} (Kafka message values); times as ISO-8601 strings.
 * Copy of market-data-service's {@code tracking.publish.CandleSeriesHistoryChangedJson}.
 */
public final class CandleSeriesHistoryChangedJson {

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private CandleSeriesHistoryChangedJson() {
    }

    public static byte[] toBytes(CandleSeriesHistoryChanged change) {
        return MAPPER.writeValueAsBytes(change);
    }

    public static CandleSeriesHistoryChanged fromBytes(byte[] json) {
        return MAPPER.readValue(json, CandleSeriesHistoryChanged.class);
    }
}
