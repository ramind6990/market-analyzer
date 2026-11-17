package com.rmrx.marketnexus.exchange.demo;

import com.rmrx.marketnexus.exchange.client.ExchangeClient;
import com.rmrx.marketnexus.exchange.client.ExchangeClients;
import com.rmrx.marketnexus.exchange.model.ExchangeException;
import com.rmrx.marketnexus.exchange.model.ExchangeType;
import com.rmrx.marketnexus.exchange.config.ExchangeConfig;
import com.rmrx.marketnexus.exchange.model.ExchangeCandle;
import com.rmrx.marketnexus.exchange.model.ExchangeOhlcRequest;
import com.rmrx.marketnexus.exchange.model.ExchangeOhlcResponse;
import com.rmrx.marketnexus.exchange.model.ExchangeSymbol;
import com.rmrx.marketnexus.exchange.model.ExchangeTimeframe;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;

/**
 * Manual demo of the generic exchange contract, the way another module would use it:
 * build an {@link ExchangeConfig}, get an {@link ExchangeClient}, and request candles.
 */
public final class ExchangeOhlcDemo {

    private ExchangeOhlcDemo() {
    }

    public static void main(String[] args) {
        ExchangeConfig config = new ExchangeConfig(
                ExchangeType.CRYPTO_NOBITEX,
                URI.create("https://apiv2.nobitex.ir"),
                "TraderBot/rm-trading-0.0.1");
        ExchangeSymbol btcUsdt = ExchangeSymbol.of("BTC", "USDT");

        try (ExchangeClient client = ExchangeClients.create(config)) {
            print(client.getOhlc(ExchangeOhlcRequest.latest(btcUsdt, ExchangeTimeframe.M1, 5)));

            Instant now = Instant.now();
            print(client.getOhlc(ExchangeOhlcRequest.range(btcUsdt, ExchangeTimeframe.D1,
                    now.minus(Duration.ofDays(4)), now)));

            try {
                client.getOhlc(ExchangeOhlcRequest.latest(ExchangeSymbol.of("NOPE", "USDT"), ExchangeTimeframe.H1, 5));
            } catch (ExchangeException e) {
                System.out.printf("Expected failure: exchange=%s http=%d code=%s%n",
                        e.exchangeType(), e.httpStatus(), e.errorCode().orElse("-"));
            }
        }
    }

    private static void print(ExchangeOhlcResponse response) {
        System.out.printf("%n%s %s %s: %d candles (%d closed), retrieved %s%n", response.exchangeType(),
                response.symbol(), response.timeframe(), response.candles().size(),
                response.closedCandles().size(), response.retrievedAt());
        System.out.printf("%-22s %14s %14s %14s %14s %16s  %s%n",
                "open time (UTC)", "open", "high", "low", "close", "volume", "closed");
        for (ExchangeCandle candle : response.candles()) {
            System.out.printf("%-22s %14s %14s %14s %14s %16s  %s%n",
                    candle.openTime(), candle.open().toPlainString(), candle.high().toPlainString(),
                    candle.low().toPlainString(), candle.close().toPlainString(),
                    candle.volume().toPlainString(), candle.closed());
        }
    }
}
