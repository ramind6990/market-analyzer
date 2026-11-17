package com.rmrx.marketanalyzer.exchange.crypto.nobitex;

import com.rmrx.marketanalyzer.exchange.crypto.nobitex.model.NobitexOhlcRequest;
import com.rmrx.marketanalyzer.exchange.crypto.nobitex.model.NobitexOhlcResponse;

import java.util.Objects;

/**
 * Public market-data endpoints of the Nobitex REST API.
 * Paging through long histories is left to the caller (e.g. a history loader).
 */
public final class NobitexMarketDataClient implements AutoCloseable {

    static final String OHLC_PATH = "/market/udf/history";

    private final NobitexHttpClient http;

    public NobitexMarketDataClient(NobitexConfig config) {
        this(new NobitexHttpClient(config));
    }

    NobitexMarketDataClient(NobitexHttpClient http) {
        this.http = Objects.requireNonNull(http, "http");
    }

    /**
     * Fetches OHLC candles ({@code GET /market/udf/history}, public, 60 requests/minute, max 500 candles per call).
     * The newest candle in the response may still be forming; check {@code NobitexCandle.isClosed(now)}.
     *
     * @return the response with status {@code ok} or {@code no_data}
     * @throws NobitexApiException on HTTP errors (e.g. 400 for an unknown symbol) or when the server reports
     *                             {@code s = "error"} (e.g. an invalid resolution)
     */
    public NobitexOhlcResponse getOhlc(NobitexOhlcRequest request) {
        Objects.requireNonNull(request, "request");
        NobitexOhlcResponse response = http.get(OHLC_PATH, request.toQueryString(), NobitexOhlcResponse.class, false);
        if (!response.isOk() && !response.isNoData()) {
            throw new NobitexApiException(200, null,
                    "Nobitex OHLC request failed: s=" + response.s() + ", errmsg=" + response.errmsg());
        }
        return response;
    }

    @Override
    public void close() {
        http.close();
    }
}
