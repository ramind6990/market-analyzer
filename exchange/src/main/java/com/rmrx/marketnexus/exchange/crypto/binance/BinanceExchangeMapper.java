package com.rmrx.marketnexus.exchange.crypto.binance;

import com.rmrx.marketnexus.exchange.config.ExchangeConfig;
import com.rmrx.marketnexus.exchange.crypto.binance.model.BinanceErrorResponse;
import com.rmrx.marketnexus.exchange.crypto.binance.model.BinanceInterval;
import com.rmrx.marketnexus.exchange.crypto.binance.model.BinanceKline;
import com.rmrx.marketnexus.exchange.model.ExchangeCandle;
import com.rmrx.marketnexus.exchange.model.ExchangeException;
import com.rmrx.marketnexus.exchange.model.ExchangeSymbol;
import com.rmrx.marketnexus.exchange.model.ExchangeTimeframe;
import com.rmrx.marketnexus.exchange.model.ExchangeType;

import java.util.Locale;
import java.util.Objects;

/** Translates between the generic exchange contract and the Binance model. */
final class BinanceExchangeMapper {

    private static final ExchangeType TYPE = ExchangeType.CRYPTO_BINANCE;

    private BinanceExchangeMapper() {
    }

    static ExchangeConfig requireBinance(ExchangeConfig config) {
        Objects.requireNonNull(config, "config");
        if (config.exchangeType() != TYPE) {
            throw new IllegalArgumentException("Expected " + TYPE + " config, got " + config.exchangeType());
        }
        return config;
    }

    static BinanceConfig toBinanceConfig(ExchangeConfig config) {
        return new BinanceConfig(config.baseUrl(), config.userAgent(), config.connectTimeout(),
                config.requestTimeout(), config.minRequestInterval(), config.maxRetries());
    }

    /** REST symbols are base + quote in upper case, e.g. {@code BTCUSDT}. */
    static String toBinanceSymbol(ExchangeSymbol symbol) {
        return (symbol.base() + symbol.quote()).toUpperCase(Locale.ROOT);
    }

    /** Stream names use the lower-case symbol, e.g. {@code btcusdt@kline_1m}. */
    static String toStreamSymbol(ExchangeSymbol symbol) {
        return toBinanceSymbol(symbol).toLowerCase(Locale.ROOT);
    }

    static BinanceInterval toBinanceInterval(ExchangeTimeframe timeframe) {
        return switch (timeframe) {
            case M1 -> BinanceInterval.MINUTE_1;
            case M5 -> BinanceInterval.MINUTE_5;
            case M15 -> BinanceInterval.MINUTE_15;
            case M30 -> BinanceInterval.MINUTE_30;
            case H1 -> BinanceInterval.HOUR_1;
            case H2 -> BinanceInterval.HOUR_2;
            case H4 -> BinanceInterval.HOUR_4;
            case H6 -> BinanceInterval.HOUR_6;
            case H12 -> BinanceInterval.HOUR_12;
            case D1 -> BinanceInterval.DAY_1;
            case D3 -> BinanceInterval.DAY_3;
            case W1 -> BinanceInterval.WEEK_1;
            case H3, D2 -> throw new ExchangeException(TYPE, "Timeframe " + timeframe + " is not supported by Binance");
        };
    }

    static ExchangeCandle toExchangeCandle(BinanceKline kline, boolean closed) {
        return new ExchangeCandle(kline.openTime(), kline.closeTime(), kline.open(), kline.high(), kline.low(),
                kline.close(), kline.volume(), closed);
    }

    static ExchangeException toExchangeException(BinanceApiException e) {
        String code = e.error().map(BinanceErrorResponse::code).map(String::valueOf).orElse(null);
        return new ExchangeException(TYPE, e.httpStatus(), code, e.getMessage(), e);
    }
}
