package com.rmrx.marketnexus.marketdata.service.reconciliation;

import com.rmrx.marketnexus.marketdata.model.Timeframe;

import java.util.List;
import java.util.Map;

/** Storage of reconciliation jobs ({@code reconciliation_job}). */
public interface ReconciliationJobRepository {

    /** Windows of open (PENDING or RUNNING) jobs per symbol, ordered by start; symbols without open jobs are absent. */
    Map<String, List<FetchWindow>> findOpenWindows(String exchange, Timeframe timeframe, List<String> symbols);

    /** Registers one PENDING job per window, with its cursor at the window start. */
    void insertAll(String exchange, String symbol, Timeframe timeframe, List<FetchWindow> windows);
}
