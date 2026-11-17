package com.rmrx.marketnexus.exchange.demo;

import com.rmrx.marketnexus.exchange.crypto.nobitex.model.NobitexCandle;
import com.rmrx.marketnexus.exchange.crypto.nobitex.model.NobitexResolution;
import com.rmrx.marketnexus.exchange.crypto.nobitex.ws.NobitexCandleListener;
import com.rmrx.marketnexus.exchange.crypto.nobitex.ws.NobitexWebSocketClient;
import com.rmrx.marketnexus.exchange.crypto.nobitex.ws.NobitexWebSocketConfig;

import java.net.URI;
import java.time.Duration;
import java.util.List;

/**
 * Manual demo: streams 1-minute candles for several symbols over one Nobitex WebSocket connection for a few minutes
 * and prints every update and every closed candle.
 * <p>
 * Optional args: {@code [comma-separated symbols] [resolution api value] [minutes]}, e.g. {@code BTCUSDT,ETHUSDT,SOLUSDT 1 3}.
 */
public final class NobitexWebSocketDemo {

    private static final String ROW = "%-8s %-9s %-22s %14s %14s %14s %14s %16s%n";

    private NobitexWebSocketDemo() {
    }

    public static void main(String[] args) throws InterruptedException {
        List<String> symbols = List.of((args.length > 0 ? args[0] : "BTCUSDT").split(","));
        NobitexResolution resolution = args.length > 1 ? NobitexResolution.fromApiValue(args[1]) : NobitexResolution.MINUTE_1;
        long minutes = args.length > 2 ? Long.parseLong(args[2]) : 3;

        NobitexWebSocketConfig config = new NobitexWebSocketConfig(
                URI.create("wss://ws.nobitex.ir/connection/websocket"),
                "TraderBot/rm-trading-0.0.1",
                "" // connection token: only needed for private channels (from GET /auth/ws/token/), empty for candles
        );

        System.out.printf("%s %s via WebSocket for %d minute(s)%n", symbols, resolution, minutes);
        System.out.printf(ROW, "event", "symbol", "open time (UTC)", "open", "high", "low", "close", "volume");

        NobitexCandleListener printer = new NobitexCandleListener() {
            @Override
            public void onUpdate(NobitexCandle forming) {
                print("update", forming);
            }

            @Override
            public void onClosed(NobitexCandle closed) {
                print("CLOSED", closed);
            }
        };

        try (NobitexWebSocketClient client = new NobitexWebSocketClient(config)) {
            for (String symbol : symbols) {
                client.subscribeCandles(symbol.strip(), resolution, printer);
            }
            client.connect();
            Thread.sleep(Duration.ofMinutes(minutes));
        }
    }

    private static void print(String event, NobitexCandle candle) {
        System.out.printf(ROW, event, candle.symbol(), candle.openTime(), candle.open().toPlainString(),
                candle.high().toPlainString(), candle.low().toPlainString(), candle.close().toPlainString(),
                candle.volume().toPlainString());
    }
}
