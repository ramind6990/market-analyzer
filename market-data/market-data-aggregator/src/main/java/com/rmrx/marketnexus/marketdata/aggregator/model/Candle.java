package com.rmrx.marketnexus.marketdata.aggregator.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Input candle as published to Kafka by ingestion ({@code m1-live}, {@code m1-reconciled}) and by reconciliation
 * ({@code h1-reconciled}). Always a closed candle. Same JSON shape as the market-data service's candle.
 */
public record Candle(
        String exchange,
        String symbol,
        Timeframe timeframe,
        Instant openTime,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        BigDecimal volume) {
}
