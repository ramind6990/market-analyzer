package com.rmrx.marketanalyzer.marketdata.service.publish;

import com.rmrx.marketanalyzer.marketdata.model.Candle;

/** Announces stored candles to downstream consumers (Kafka topic {@code m1-live}). */
public interface CandlePublisher {

    /** Sends the candle asynchronously; failures are logged by the implementation, never thrown. */
    void publish(Candle candle);
}
