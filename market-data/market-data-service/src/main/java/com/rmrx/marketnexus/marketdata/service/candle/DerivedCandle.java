package com.rmrx.marketnexus.marketdata.service.candle;

import com.rmrx.marketnexus.marketdata.model.Candle;

/**
 * A derived candle (M5..D1 from the Flink aggregator) as stored: partial ({@code closed = false}) or final.
 */
public record DerivedCandle(Candle candle, boolean closed) {

    /** Row identity {@code exchange:symbol:timeframe:openTime}. */
    String rowKey() {
        return candle.marketKey() + ":" + candle.timeframe() + ":" + candle.openTime().getEpochSecond();
    }
}
