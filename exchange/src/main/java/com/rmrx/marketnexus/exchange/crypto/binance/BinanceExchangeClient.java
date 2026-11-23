package com.rmrx.marketnexus.exchange.crypto.binance;

import com.rmrx.marketnexus.exchange.client.ExchangeClient;
import com.rmrx.marketnexus.exchange.config.ExchangeConfig;
import com.rmrx.marketnexus.exchange.crypto.binance.model.BinanceInterval;
import com.rmrx.marketnexus.exchange.crypto.binance.model.BinanceKline;
import com.rmrx.marketnexus.exchange.crypto.binance.model.BinanceKlinesRequest;
import com.rmrx.marketnexus.exchange.model.ExchangeCandle;
import com.rmrx.marketnexus.exchange.model.ExchangeException;
import com.rmrx.marketnexus.exchange.model.ExchangeOhlcRequest;
import com.rmrx.marketnexus.exchange.model.ExchangeOhlcResponse;
import com.rmrx.marketnexus.exchange.model.ExchangeType;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** {@link ExchangeClient} for Binance spot: maps the generic contract onto the Binance klines endpoint. */
public final class BinanceExchangeClient implements ExchangeClient {

    private final BinanceMarketDataClient marketData;
    private final Clock clock;

    public BinanceExchangeClient(ExchangeConfig config) {
        this(new BinanceMarketDataClient(BinanceExchangeMapper.toBinanceConfig(
                BinanceExchangeMapper.requireBinance(config))), Clock.systemUTC());
    }

    BinanceExchangeClient(BinanceMarketDataClient marketData, Clock clock) {
        this.marketData = Objects.requireNonNull(marketData, "marketData");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public ExchangeType exchangeType() {
        return ExchangeType.CRYPTO_BINANCE;
    }

    /**
     * Binance returns at most {@value BinanceKlinesRequest#MAX_LIMIT} klines per call; larger limits are capped.
     * <ul>
     *   <li>{@code limit} set: the newest {@code limit} klines up to {@code to} ({@code endTime} only), then those
     *       before {@code from} are dropped;</li>
     *   <li>only {@code from}: klines from {@code from} on (oldest first, at most 1,000);</li>
     *   <li>neither: the newest 1,000 klines.</li>
     * </ul>
     * {@code endTime} is the last millisecond of the kline opening at {@code to}, so that kline is included whether
     * Binance compares {@code endTime} with open or close times.
     */
    @Override
    public ExchangeOhlcResponse getOhlc(ExchangeOhlcRequest request) {
        Objects.requireNonNull(request, "request");
        String symbol = BinanceExchangeMapper.toBinanceSymbol(request.symbol());
        BinanceInterval interval = BinanceExchangeMapper.toBinanceInterval(request.timeframe());
        Instant to = request.to() != null ? request.to() : clock.instant();
        Instant endTime = to.plus(interval.duration()).minusMillis(1);

        int limit = request.limit() != null
                ? Math.min(request.limit(), BinanceKlinesRequest.MAX_LIMIT)
                : BinanceKlinesRequest.MAX_LIMIT;
        Instant startTime = request.limit() == null ? request.from() : null;
        BinanceKlinesRequest binanceRequest = new BinanceKlinesRequest(symbol, interval, startTime, endTime, limit);

        List<BinanceKline> klines;
        try {
            klines = marketData.getKlines(binanceRequest);
        } catch (BinanceApiException e) {
            throw BinanceExchangeMapper.toExchangeException(e);
        } catch (IllegalStateException | NumberFormatException e) {
            throw new ExchangeException(exchangeType(), 200, null,
                    "Malformed Binance klines response: " + e.getMessage(), e);
        }

        Instant now = clock.instant();
        List<ExchangeCandle> candles = klines.stream()
                .filter(k -> request.from() == null || !k.openTime().isBefore(request.from()))
                .filter(k -> !k.openTime().isAfter(to))
                .map(k -> BinanceExchangeMapper.toExchangeCandle(k, k.isClosed(now)))
                .toList();
        return new ExchangeOhlcResponse(exchangeType(), request.symbol(), request.timeframe(), candles, now);
    }

    @Override
    public void close() {
        marketData.close();
    }
}
