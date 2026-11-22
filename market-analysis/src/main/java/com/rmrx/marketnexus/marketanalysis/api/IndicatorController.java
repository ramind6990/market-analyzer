package com.rmrx.marketnexus.marketanalysis.api;

import com.rmrx.marketnexus.marketanalysis.api.dto.BarDto;
import com.rmrx.marketnexus.marketanalysis.api.dto.IndicatorDto;
import com.rmrx.marketnexus.marketanalysis.api.dto.IndicatorResponse;
import com.rmrx.marketnexus.marketanalysis.job.SeriesKey;
import com.rmrx.marketnexus.marketanalysis.service.IndicatorHistory;
import com.rmrx.marketnexus.marketanalysis.service.IndicatorService;
import com.rmrx.marketnexus.marketdata.model.Timeframe;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Stored indicator values of a series.
 * <pre>
 * GET /api/v1/indicators?exchange=CRYPTO_NOBITEX&amp;symbol=BTC/USDT&amp;timeframe=M1&amp;type=MACD&amp;fast=12&amp;limit=100
 * </pre>
 * Every query parameter besides the fixed ones is an indicator parameter; omitted ones take their defaults.
 * Without {@code type}: all indicators of the series (its indicator set for the timeframe).
 */
@RestController
@RequestMapping("/api/v1/indicators")
public class IndicatorController {

    private static final Set<String> FIXED = Set.of("exchange", "symbol", "timeframe", "type", "limit");
    private static final int MAX_LIMIT = 1000;

    private final IndicatorService service;
    private final IndicatorSpecParser parser;

    IndicatorController(IndicatorService service, IndicatorSpecParser parser) {
        this.service = service;
        this.parser = parser;
    }

    @GetMapping
    public IndicatorResponse fetchIndicators(@RequestParam("exchange") String exchange,
                                             @RequestParam("symbol") String symbol,
                                             @RequestParam("timeframe") String timeframe,
                                             @RequestParam(name = "type", required = false) String type,
                                             @RequestParam(name = "limit", defaultValue = "100") int limit,
                                             @RequestParam Map<String, String> query) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_LIMIT);
        }
        SeriesKey series = new SeriesKey(exchange, symbol, parseTimeframe(timeframe));
        Map<String, String> params = new LinkedHashMap<>(query);
        params.keySet().removeAll(FIXED);

        List<IndicatorHistory> histories;
        if (type == null || type.isBlank()) {
            if (!params.isEmpty()) {
                throw new IllegalArgumentException("indicator parameters " + params.keySet() + " need a type");
            }
            histories = service.fetchAllIndicators(series, limit);
        } else {
            histories = List.of(service.fetchIndicator(series, parser.parse(type, params), limit));
        }
        return new IndicatorResponse(exchange, symbol, series.timeframe().name(),
                histories.stream().map(IndicatorController::toDto).toList());
    }

    private static IndicatorDto toDto(IndicatorHistory history) {
        return new IndicatorDto(history.spec().type(), IndicatorSpecParser.params(history.spec()),
                history.bars().stream().map(bar -> new BarDto(bar.openTime(), bar.values())).toList());
    }

    private static Timeframe parseTimeframe(String timeframe) {
        try {
            return Timeframe.valueOf(timeframe.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("unknown timeframe " + timeframe);
        }
    }
}
