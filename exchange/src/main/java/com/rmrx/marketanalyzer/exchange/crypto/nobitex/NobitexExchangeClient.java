package com.rmrx.marketanalyzer.exchange.crypto.nobitex;

import com.rmrx.marketanalyzer.exchange.client.ExchangeClient;
import com.rmrx.marketanalyzer.exchange.model.ExchangeException;
import com.rmrx.marketanalyzer.exchange.model.ExchangeType;
import com.rmrx.marketanalyzer.exchange.config.ExchangeConfig;
import com.rmrx.marketanalyzer.exchange.crypto.nobitex.model.NobitexCandle;
import com.rmrx.marketanalyzer.exchange.crypto.nobitex.model.NobitexOhlcRequest;
import com.rmrx.marketanalyzer.exchange.crypto.nobitex.model.NobitexOhlcResponse;
import com.rmrx.marketanalyzer.exchange.crypto.nobitex.model.NobitexResolution;
import com.rmrx.marketanalyzer.exchange.model.ExchangeCandle;
import com.rmrx.marketanalyzer.exchange.model.ExchangeOhlcRequest;
import com.rmrx.marketanalyzer.exchange.model.ExchangeOhlcResponse;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** {@link ExchangeClient} for Nobitex: maps the generic contract onto the Nobitex API clients. */
public final class NobitexExchangeClient implements ExchangeClient {

    private final NobitexMarketDataClient marketData;
    private final Clock clock;

    public NobitexExchangeClient(ExchangeConfig config) {
        this(new NobitexMarketDataClient(NobitexExchangeMapper.toNobitexConfig(requireNobitex(config))),
                Clock.systemUTC());
    }

    NobitexExchangeClient(NobitexMarketDataClient marketData, Clock clock) {
        this.marketData = Objects.requireNonNull(marketData, "marketData");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public ExchangeType exchangeType() {
        return ExchangeType.CRYPTO_NOBITEX;
    }

    /**
     * Nobitex returns at most {@value NobitexOhlcRequest#MAX_COUNTBACK} candles per call; larger limits are capped.
     * When {@code limit} is set, Nobitex ignores {@code from}, so candles before {@code from} are filtered out here.
     */
    @Override
    public ExchangeOhlcResponse getOhlc(ExchangeOhlcRequest request) {
        Objects.requireNonNull(request, "request");
        String symbol = NobitexExchangeMapper.toNobitexSymbol(request.symbol());
        NobitexResolution resolution = NobitexExchangeMapper.toNobitexResolution(request.timeframe());
        Instant to = request.to() != null ? request.to() : clock.instant();

        Integer countback;
        if (request.limit() != null) {
            countback = Math.min(request.limit(), NobitexOhlcRequest.MAX_COUNTBACK);
        } else if (request.from() == null) {
            countback = NobitexOhlcRequest.MAX_COUNTBACK;
        } else {
            countback = null;
        }
        NobitexOhlcRequest nobitexRequest =
                new NobitexOhlcRequest(symbol, resolution, to, request.from(), countback, null);

        List<NobitexCandle> nobitexCandles;
        try {
            NobitexOhlcResponse response = marketData.getOhlc(nobitexRequest);
            nobitexCandles = response.toCandles(symbol, resolution);
        } catch (NobitexApiException e) {
            throw NobitexExchangeMapper.toExchangeException(e);
        } catch (IllegalStateException e) {
            throw new ExchangeException(exchangeType(), 200, null, "Malformed Nobitex OHLC response: " + e.getMessage(), e);
        }

        Instant now = clock.instant();
        List<ExchangeCandle> candles = nobitexCandles.stream()
                .filter(c -> request.from() == null || !c.openTime().isBefore(request.from()))
                .filter(c -> !c.openTime().isAfter(to))
                .map(c -> NobitexExchangeMapper.toExchangeCandle(c, now))
                .toList();
        return new ExchangeOhlcResponse(exchangeType(), request.symbol(), request.timeframe(), candles, now);
    }

    @Override
    public void close() {
        marketData.close();
    }

    private static ExchangeConfig requireNobitex(ExchangeConfig config) {
        Objects.requireNonNull(config, "config");
        if (config.exchangeType() != ExchangeType.CRYPTO_NOBITEX) {
            throw new IllegalArgumentException("Expected " + ExchangeType.CRYPTO_NOBITEX + " config, got "
                    + config.exchangeType());
        }
        return config;
    }
}
