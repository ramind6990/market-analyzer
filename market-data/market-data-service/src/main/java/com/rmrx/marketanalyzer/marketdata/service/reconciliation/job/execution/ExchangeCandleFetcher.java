package com.rmrx.marketanalyzer.marketdata.service.reconciliation.job.execution;

import com.rmrx.marketanalyzer.exchange.client.ExchangeClient;
import com.rmrx.marketanalyzer.exchange.model.ExchangeCandle;
import com.rmrx.marketanalyzer.exchange.model.ExchangeOhlcRequest;
import com.rmrx.marketanalyzer.exchange.model.ExchangeOhlcResponse;
import com.rmrx.marketanalyzer.exchange.model.ExchangeSymbol;
import com.rmrx.marketanalyzer.exchange.model.ExchangeTimeframe;
import com.rmrx.marketanalyzer.marketdata.model.Candle;
import com.rmrx.marketanalyzer.marketdata.model.Timeframe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * {@link CandleFetcher} over the configured exchange's REST API ({@link ExchangeClient#getOhlc}).
 * <p>
 * Requests exactly the candles of {@code [from, to]} (limit = their count, counted back from {@code to}), keeps only
 * closed ones and maps them to {@link Candle}. Exchange errors ({@code ExchangeException}) propagate, so the executor
 * retries with backoff.
 */
@Component
public class ExchangeCandleFetcher implements CandleFetcher {

    private static final Logger log = LoggerFactory.getLogger(ExchangeCandleFetcher.class);

    private final ExchangeClient client;

    public ExchangeCandleFetcher(ExchangeClient client) {
        this.client = Objects.requireNonNull(client, "client");
    }

    @Override
    public List<Candle> fetch(String exchange, String symbol, Timeframe timeframe, Instant from, Instant to) {
        if (!client.exchangeType().name().equals(exchange)) {
            throw new IllegalArgumentException("Job for exchange " + exchange + ", but the configured exchange is "
                    + client.exchangeType());
        }
        ExchangeSymbol exchangeSymbol = parse(symbol);
        ExchangeTimeframe exchangeTimeframe = ExchangeTimeframe.valueOf(timeframe.name());
        int count = (int) (Duration.between(from, to).dividedBy(timeframe.duration()) + 1);

        ExchangeOhlcResponse response = client.getOhlc(
                new ExchangeOhlcRequest(exchangeSymbol, exchangeTimeframe, from, to, count));
        List<Candle> candles = response.closedCandles().stream()
                .map(c -> toCandle(exchange, symbol, timeframe, c))
                .toList();
        log.debug("Fetched {} {} {} [{} .. {}]: {} closed candle(s) of {} requested", exchange, symbol, timeframe,
                from, to, candles.size(), count);
        return candles;
    }

    /** {@code "BTC/USDT"} → {@code ExchangeSymbol(BTC, USDT)}. */
    private static ExchangeSymbol parse(String symbol) {
        int slash = symbol.indexOf('/');
        if (slash <= 0 || slash == symbol.length() - 1) {
            throw new IllegalArgumentException("Symbol must be BASE/QUOTE: " + symbol);
        }
        return ExchangeSymbol.of(symbol.substring(0, slash), symbol.substring(slash + 1));
    }

    private static Candle toCandle(String exchange, String symbol, Timeframe timeframe, ExchangeCandle candle) {
        return new Candle(exchange, symbol, timeframe, candle.openTime(), candle.open(), candle.high(), candle.low(),
                candle.close(), candle.volume());
    }
}
