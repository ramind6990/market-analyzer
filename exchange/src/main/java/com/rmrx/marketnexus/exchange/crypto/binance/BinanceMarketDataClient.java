package com.rmrx.marketnexus.exchange.crypto.binance;

import com.rmrx.marketnexus.exchange.crypto.binance.model.BinanceKline;
import com.rmrx.marketnexus.exchange.crypto.binance.model.BinanceKlinesRequest;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Client for the public Binance spot market-data endpoints (currently klines only). */
public final class BinanceMarketDataClient implements AutoCloseable {

    static final String KLINES_PATH = "/api/v3/klines";

    private final BinanceHttpClient http;

    public BinanceMarketDataClient(BinanceConfig config) {
        this.http = new BinanceHttpClient(config);
    }

    /**
     * {@code GET /api/v3/klines} (weight 2): at most {@value BinanceKlinesRequest#MAX_LIMIT} klines, oldest first.
     * The newest kline may still be forming.
     */
    public List<BinanceKline> getKlines(BinanceKlinesRequest request) {
        Objects.requireNonNull(request, "request");
        JsonNode body = http.get(KLINES_PATH, request.toQueryString());
        if (body == null || !body.isArray()) {
            throw new BinanceApiException(200, null, "Unexpected klines response: " + body);
        }
        List<BinanceKline> klines = new ArrayList<>(body.size());
        for (JsonNode array : body) {
            klines.add(BinanceKline.fromRestArray(request.symbol(), request.interval(), array));
        }
        return klines;
    }

    @Override
    public void close() {
        http.close();
    }
}
