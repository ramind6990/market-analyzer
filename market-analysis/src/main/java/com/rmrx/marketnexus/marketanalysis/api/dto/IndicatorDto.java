package com.rmrx.marketnexus.marketanalysis.api.dto;

import java.util.List;
import java.util.Map;

/**
 * @param params effective parameters (defaults filled in): numbers as numbers, choices and price fields as text
 * @param bars   newest first
 */
public record IndicatorDto(String type, Map<String, Object> params, List<BarDto> bars) {
}
