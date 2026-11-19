package com.rmrx.marketnexus.marketdata.service.publish;

import com.rmrx.marketnexus.marketdata.model.Candle;

/**
 * Announces stored candles to downstream consumers (Kafka topics {@code m1-live}, {@code m1-reconciled},
 * {@code h1-reconciled}). Key = market ({@code exchange:symbol}), value = {@code CandleJson}.
 */
public interface CandlePublisher {

    /** Sends the candle asynchronously to {@code topic}; failures are logged by the implementation, never thrown. */
    void publish(String topic, Candle candle);
}
