package com.rmrx.marketanalyzer.exchange.crypto.nobitex;

import com.rmrx.marketanalyzer.exchange.model.ExchangeException;
import com.rmrx.marketanalyzer.exchange.model.ExchangeType;
import com.rmrx.marketanalyzer.exchange.config.ExchangeConfig;
import com.rmrx.marketanalyzer.exchange.crypto.nobitex.model.NobitexCandle;
import com.rmrx.marketanalyzer.exchange.crypto.nobitex.model.NobitexErrorResponse;
import com.rmrx.marketanalyzer.exchange.crypto.nobitex.model.NobitexResolution;
import com.rmrx.marketanalyzer.exchange.model.ExchangeCandle;
import com.rmrx.marketanalyzer.exchange.model.ExchangeSymbol;
import com.rmrx.marketanalyzer.exchange.model.ExchangeTimeframe;

import java.time.Instant;

/** Translates between the generic exchange contract and the Nobitex model. */
final class NobitexExchangeMapper {

    private static final ExchangeType TYPE = ExchangeType.CRYPTO_NOBITEX;

    private NobitexExchangeMapper() {
    }

    static NobitexConfig toNobitexConfig(ExchangeConfig config) {
        NobitexCredentials credentials = config.findCredentials()
                .map(c -> new NobitexCredentials(c.apiKey(), c.secretKey()))
                .orElse(null);
        return new NobitexConfig(config.baseUrl(), config.userAgent(), config.connectTimeout(),
                config.requestTimeout(), config.minRequestInterval(), config.maxRetries(), credentials);
    }

    /** Nobitex market symbols are base + quote without separator, e.g. {@code BTCUSDT}, {@code BTCIRT}. */
    static String toNobitexSymbol(ExchangeSymbol symbol) {
        return symbol.base() + symbol.quote();
    }

    static NobitexResolution toNobitexResolution(ExchangeTimeframe timeframe) {
        return switch (timeframe) {
            case M1 -> NobitexResolution.MINUTE_1;
            case M5 -> NobitexResolution.MINUTE_5;
            case M15 -> NobitexResolution.MINUTE_15;
            case M30 -> NobitexResolution.MINUTE_30;
            case H1 -> NobitexResolution.HOUR_1;
            case H3 -> NobitexResolution.HOUR_3;
            case H4 -> NobitexResolution.HOUR_4;
            case H6 -> NobitexResolution.HOUR_6;
            case H12 -> NobitexResolution.HOUR_12;
            case D1 -> NobitexResolution.DAY_1;
            case D2 -> NobitexResolution.DAY_2;
            case D3 -> NobitexResolution.DAY_3;
            case H2, W1 -> throw new ExchangeException(TYPE, "Timeframe " + timeframe + " is not supported by Nobitex");
        };
    }

    static ExchangeCandle toExchangeCandle(NobitexCandle candle, Instant now) {
        return new ExchangeCandle(candle.openTime(), candle.closeTime(), candle.open(), candle.high(),
                candle.low(), candle.close(), candle.volume(), candle.isClosed(now));
    }

    static ExchangeException toExchangeException(NobitexApiException e) {
        String code = e.error().map(NobitexErrorResponse::code).orElse(null);
        return new ExchangeException(TYPE, e.httpStatus(), code, e.getMessage(), e);
    }
}
