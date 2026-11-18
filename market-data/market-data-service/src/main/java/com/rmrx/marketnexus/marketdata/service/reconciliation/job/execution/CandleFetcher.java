package com.rmrx.marketnexus.marketdata.service.reconciliation.job.execution;

import com.rmrx.marketnexus.marketdata.model.Candle;
import com.rmrx.marketnexus.marketdata.model.Timeframe;

import java.time.Instant;
import java.util.List;

/** Fetches closed candles of one series from the exchange (REST), for reconciliation. */
public interface CandleFetcher {

    /**
     * @param from first open time (inclusive)
     * @param to   last open time (inclusive)
     * @return the candles the exchange has in that range (may be fewer than requested)
     */
    List<Candle> fetch(String exchange, String symbol, Timeframe timeframe, Instant from, Instant to);
}
