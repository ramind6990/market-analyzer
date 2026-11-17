package com.rmrx.marketanalyzer.exchange.model;

import java.util.Locale;
import java.util.Objects;

/**
 * A trading pair, independent of how a given exchange spells it
 * ({@code BTCUSDT}, {@code BTC-USDT}, {@code BTC/USDT}, ...).
 *
 * @param base  asset being bought or sold, e.g. {@code BTC}
 * @param quote asset prices are expressed in, e.g. {@code USDT}
 */
public record ExchangeSymbol(String base, String quote) {

    public ExchangeSymbol {
        base = normalize(base, "base");
        quote = normalize(quote, "quote");
    }

    public static ExchangeSymbol of(String base, String quote) {
        return new ExchangeSymbol(base, quote);
    }

    @Override
    public String toString() {
        return base + "/" + quote;
    }

    private static String normalize(String value, String name) {
        Objects.requireNonNull(value, name);
        String normalized = value.strip().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return normalized;
    }
}
