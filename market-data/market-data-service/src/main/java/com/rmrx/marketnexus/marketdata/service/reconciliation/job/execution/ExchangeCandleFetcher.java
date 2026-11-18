package com.rmrx.marketnexus.marketdata.service.reconciliation.job.execution;

import com.rmrx.marketnexus.marketdata.model.Candle;
import com.rmrx.marketnexus.marketdata.model.Timeframe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * {@link CandleFetcher} over the exchange REST API.
 * <p>
 * NOT IMPLEMENTED YET: {@link #fetch} currently returns {@link #dummyCandles dummy candles} for testing. To be implemented
 * with {@code ExchangeClient.getOhlc(...)} (exchange module): map symbol/timeframe to {@code ExchangeSymbol}/
 * {@code ExchangeTimeframe}, request {@code [from, to]}, keep closed candles and map {@code ExchangeCandle} → {@code Candle}.
 */
@Component
public class ExchangeCandleFetcher implements CandleFetcher {

    private static final Logger log = LoggerFactory.getLogger(ExchangeCandleFetcher.class);

    private static final Map<String, BigDecimal> START_PRICES = Map.of(
            "BTC/USDT", new BigDecimal("65000"),
            "ETH/USDT", new BigDecimal("1800"),
            "SOL/USDT", new BigDecimal("71"));
    private static final BigDecimal DEFAULT_START_PRICE = new BigDecimal("100");
    private static final double MAX_MOVE = 0.002;

    @Override
    public List<Candle> fetch(String exchange, String symbol, Timeframe timeframe, Instant from, Instant to) {
        List<Candle> candles = dummyCandles(exchange, symbol, timeframe, from, to);
        log.info("Fetch {} {} {} [{} .. {}]: returning {} DUMMY candle(s) (REST not implemented yet)", exchange, symbol,
                timeframe, from, to, candles.size());
        return candles;
    }

    /** Temporary test data: one random-walk candle for every open time in {@code [from, to]}, stepping by the timeframe. */
    List<Candle> dummyCandles(String exchange, String symbol, Timeframe timeframe, Instant from, Instant to) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        BigDecimal price = START_PRICES.getOrDefault(symbol, DEFAULT_START_PRICE);
        int scale = Math.max(price.scale(), 2);
        List<Candle> candles = new ArrayList<>();
        for (Instant open = from; !open.isAfter(to); open = open.plus(timeframe.duration())) {
            BigDecimal openPrice = move(price, scale, random);
            BigDecimal closePrice = move(openPrice, scale, random);
            BigDecimal high = openPrice.max(closePrice)
                    .multiply(BigDecimal.valueOf(1 + random.nextDouble() * MAX_MOVE / 2)).setScale(scale, RoundingMode.HALF_UP);
            BigDecimal low = openPrice.min(closePrice)
                    .multiply(BigDecimal.valueOf(1 - random.nextDouble() * MAX_MOVE / 2)).setScale(scale, RoundingMode.HALF_UP);
            BigDecimal volume = BigDecimal.valueOf(random.nextDouble() * 0.05).setScale(6, RoundingMode.HALF_UP);
            candles.add(new Candle(exchange, symbol, timeframe, open, openPrice, high, low, closePrice, volume));
            price = closePrice;
        }
        return candles;
    }

    private static BigDecimal move(BigDecimal price, int scale, ThreadLocalRandom random) {
        double factor = 1 + (random.nextDouble() * 2 - 1) * MAX_MOVE;
        return price.multiply(BigDecimal.valueOf(factor)).setScale(scale, RoundingMode.HALF_UP);
    }
}
