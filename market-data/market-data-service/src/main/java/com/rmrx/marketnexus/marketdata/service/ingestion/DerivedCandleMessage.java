package com.rmrx.marketnexus.marketdata.service.ingestion;

import com.rmrx.marketnexus.marketdata.model.Candle;
import com.rmrx.marketnexus.marketdata.model.Timeframe;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A message on {@code candles-derived}: a progressive derived candle (M5..D1) from the Flink candle aggregator.
 * Same JSON shape as the aggregator's {@code CandleUpdate}.
 *
 * @param reconciled true if the candle (or any of its children) came from REST reconciliation
 * @param closed     true for the final version of the candle; false while it is still forming
 */
public record DerivedCandleMessage(
        String exchange,
        String symbol,
        Timeframe timeframe,
        Instant openTime,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        BigDecimal volume,
        boolean reconciled,
        boolean closed) {

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    public static DerivedCandleMessage fromBytes(byte[] json) {
        return MAPPER.readValue(json, DerivedCandleMessage.class);
    }

    public Candle candle() {
        return new Candle(exchange, symbol, timeframe, openTime, open, high, low, close, volume);
    }
}
