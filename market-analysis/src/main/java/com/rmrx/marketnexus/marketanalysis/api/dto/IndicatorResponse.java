package com.rmrx.marketnexus.marketanalysis.api.dto;

import java.util.List;

/** Values of one or all indicators of one series; each indicator's bars newest first. */
public record IndicatorResponse(String exchange, String symbol, String timeframe, List<IndicatorDto> indicators) {
}
