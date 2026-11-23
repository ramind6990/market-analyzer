package com.rmrx.marketnexus.exchange.crypto.binance.ws;

import com.rmrx.marketnexus.exchange.crypto.binance.model.BinanceInterval;
import com.rmrx.marketnexus.exchange.crypto.binance.model.BinanceKline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Binance market-stream client on the JDK {@link WebSocket} (combined-stream endpoint, kline streams).
 * <ul>
 *   <li>Subscriptions are sent as {@code {"method":"SUBSCRIBE","params":[...],"id":n}}; Binance accepts at most
 *       5 incoming messages per second per connection, so sends are spaced by {@value #SEND_SPACING_MS} ms and
 *       batched ({@value #STREAMS_PER_MESSAGE} streams per message);</li>
 *   <li>a closed kline is reported by the event field {@code x = true};</li>
 *   <li>the server sends a ping about every 20 s; the JDK WebSocket answers with a pong automatically;</li>
 *   <li>a connection is dropped by Binance after 24 h: the client reconnects proactively after
 *       {@value #PROACTIVE_RECONNECT_HOURS} h, and after any loss with backoff (1 s doubling to 60 s), then
 *       resubscribes every stream. Klines missed meanwhile are recovered by REST reconciliation.</li>
 * </ul>
 * Connection limits (24 h lifetime, 1,024 streams per connection, ping interval) follow Binance's published rules and
 * were not verified against a live connection.
 */
public final class BinanceWebSocketClient implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(BinanceWebSocketClient.class);

    static final long SEND_SPACING_MS = 250;
    static final int STREAMS_PER_MESSAGE = 100;
    static final int MAX_STREAMS = 1024;
    static final long PROACTIVE_RECONNECT_HOURS = 23;
    private static final Duration RECONNECT_BASE_DELAY = Duration.ofSeconds(1);
    private static final Duration RECONNECT_MAX_DELAY = Duration.ofSeconds(60);

    private final BinanceWebSocketConfig config;
    private final HttpClient http;
    private final ObjectMapper mapper = JsonMapper.builder().build();
    /** Single thread: connects, sends (the JDK WebSocket forbids overlapping sends) and schedules reconnects. */
    private final ScheduledExecutorService scheduler;
    private final Map<String, Subscription> subscriptions = new ConcurrentHashMap<>();
    private final AtomicLong requestIds = new AtomicLong();

    // Touched on the scheduler thread only (except volatile reads).
    private volatile WebSocket webSocket;
    private volatile long generation;
    private volatile boolean closed;
    private boolean started;
    private int failedAttempts;
    private long nextSendAtMillis;

    public BinanceWebSocketClient(BinanceWebSocketConfig config) {
        this.config = Objects.requireNonNull(config, "config");
        this.http = HttpClient.newBuilder().connectTimeout(config.connectTimeout()).build();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "binance-ws");
            thread.setDaemon(true);
            return thread;
        });
    }

    /** Starts connecting; reconnects automatically with backoff after failures. */
    public void connect() {
        scheduler.execute(() -> {
            if (!started && !closed) {
                started = true;
                open();
            }
        });
    }

    /**
     * Subscribes to {@code <symbol>@kline_<interval>}. Can be called before or after {@link #connect()}.
     *
     * @param symbol e.g. {@code BTCUSDT} (case does not matter)
     */
    public void subscribeCandles(String symbol, BinanceInterval interval, BinanceCandleListener listener) {
        Objects.requireNonNull(symbol, "symbol");
        Objects.requireNonNull(interval, "interval");
        Objects.requireNonNull(listener, "listener");
        String upperSymbol = symbol.strip().toUpperCase(Locale.ROOT);
        String stream = upperSymbol.toLowerCase(Locale.ROOT) + "@kline_" + interval.apiValue();
        if (subscriptions.size() >= MAX_STREAMS) {
            throw new IllegalStateException("Binance allows at most " + MAX_STREAMS + " streams per connection");
        }
        if (subscriptions.putIfAbsent(stream, new Subscription(upperSymbol, interval, listener)) != null) {
            throw new IllegalStateException("Already subscribed to " + stream);
        }
        scheduler.execute(() -> {
            if (webSocket != null) {
                sendSubscribe(webSocket, List.of(stream));
            }
        });
    }

    @Override
    public void close() {
        closed = true;
        scheduler.execute(() -> {
            WebSocket current = webSocket;
            webSocket = null;
            if (current != null) {
                current.sendClose(WebSocket.NORMAL_CLOSURE, "bye").exceptionally(e -> null);
            }
        });
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        WebSocket current = webSocket;
        if (current != null) {
            current.abort();
        }
        http.close();
    }

    // ---- connection lifecycle (scheduler thread) ----

    private void open() {
        long myGeneration = ++generation;
        log.info("Binance WebSocket connecting to {}", config.combinedStreamUrl());
        http.newWebSocketBuilder()
                .header("User-Agent", config.userAgent())
                .connectTimeout(config.connectTimeout())
                .buildAsync(config.combinedStreamUrl(), new Listener(myGeneration))
                .whenComplete((ws, error) -> runOnScheduler(() -> {
                    if (error != null) {
                        log.warn("Binance WebSocket connect failed: {}", error.toString());
                        scheduleReconnect(myGeneration);
                    } else {
                        onOpened(ws, myGeneration);
                    }
                }));
    }

    private void onOpened(WebSocket ws, long myGeneration) {
        if (closed || myGeneration != generation) {
            ws.abort();
            return;
        }
        webSocket = ws;
        failedAttempts = 0;
        log.info("Binance WebSocket connected, resubscribing {} stream(s)", subscriptions.size());
        List<String> streams = new ArrayList<>(subscriptions.keySet());
        for (int from = 0; from < streams.size(); from += STREAMS_PER_MESSAGE) {
            sendSubscribe(ws, streams.subList(from, Math.min(from + STREAMS_PER_MESSAGE, streams.size())));
        }
        scheduler.schedule(() -> {
            if (!closed && myGeneration == generation) {
                log.info("Binance WebSocket reconnecting before the 24 h connection limit");
                reconnectNow();
            }
        }, PROACTIVE_RECONNECT_HOURS, TimeUnit.HOURS);
    }

    private void reconnectNow() {
        WebSocket old = webSocket;
        webSocket = null;
        if (old != null) {
            old.abort();
        }
        open();
    }

    /** Called (on the scheduler) when the connection of {@code lostGeneration} failed or closed. */
    private void scheduleReconnect(long lostGeneration) {
        if (closed || lostGeneration != generation) {
            return;
        }
        webSocket = null;
        failedAttempts++;
        long delayMs = Math.min(RECONNECT_MAX_DELAY.toMillis(),
                RECONNECT_BASE_DELAY.toMillis() << Math.min(failedAttempts - 1, 6));
        log.info("Binance WebSocket reconnecting in {} ms (attempt {})", delayMs, failedAttempts);
        scheduler.schedule(() -> {
            if (!closed && lostGeneration == generation) {
                open();
            }
        }, delayMs, TimeUnit.MILLISECONDS);
    }

    private void sendSubscribe(WebSocket ws, List<String> streams) {
        ObjectNode message = mapper.createObjectNode();
        message.put("method", "SUBSCRIBE");
        ArrayNode params = message.putArray("params");
        streams.forEach(params::add);
        message.put("id", requestIds.incrementAndGet());
        String text = mapper.writeValueAsString(message);

        long now = System.currentTimeMillis();
        long sendAt = Math.max(now, nextSendAtMillis);
        nextSendAtMillis = sendAt + SEND_SPACING_MS;
        scheduler.schedule(() -> {
            if (ws != webSocket) {
                return; // connection replaced meanwhile; the new one resubscribes everything
            }
            try {
                ws.sendText(text, true).join();
                log.info("Subscribed to {}", streams);
            } catch (RuntimeException e) {
                log.warn("Binance SUBSCRIBE failed for {}: {}", streams, e.toString());
            }
        }, sendAt - now, TimeUnit.MILLISECONDS);
    }

    // ---- inbound messages (WebSocket delivery thread, one at a time) ----

    private void handle(String text) {
        JsonNode root = mapper.readTree(text);
        if (root.has("error")) {
            log.warn("Binance WebSocket error response: {}", text);
            return;
        }
        JsonNode data = root.get("data");
        if (data == null) {
            return; // subscribe acknowledgement {"result":null,"id":n}
        }
        String stream = root.path("stream").asString();
        Subscription subscription = subscriptions.get(stream);
        if (subscription == null || !"kline".equals(data.path("e").asString())) {
            return;
        }
        JsonNode k = data.get("k");
        BinanceKline kline = BinanceKline.fromStreamObject(subscription.symbol(), subscription.interval(), k);
        if (k.path("x").asBoolean()) {
            subscription.listener().onClosed(kline);
        } else {
            subscription.listener().onUpdate(kline);
        }
    }

    private record Subscription(String symbol, BinanceInterval interval, BinanceCandleListener listener) {
    }

    private final class Listener implements WebSocket.Listener {

        private final long myGeneration;
        private final StringBuilder buffer = new StringBuilder();

        Listener(long myGeneration) {
            this.myGeneration = myGeneration;
        }

        @Override
        public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
            buffer.append(data);
            if (last) {
                String text = buffer.toString();
                buffer.setLength(0);
                // Exceptions must not escape: they would close the connection.
                try {
                    handle(text);
                } catch (RuntimeException e) {
                    log.warn("Failed to handle Binance message: {}", abbreviate(text), e);
                }
            }
            ws.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
            log.info("Binance WebSocket closed: {} {}", statusCode, reason);
            runOnScheduler(() -> scheduleReconnect(myGeneration));
            return null;
        }

        @Override
        public void onError(WebSocket ws, Throwable error) {
            log.warn("Binance WebSocket error: {}", error.toString());
            runOnScheduler(() -> scheduleReconnect(myGeneration));
        }
    }

    /** Runs a task on the scheduler thread; ignored once the client is closed. */
    private void runOnScheduler(Runnable task) {
        try {
            scheduler.execute(task);
        } catch (RejectedExecutionException e) {
            // closed
        }
    }

    private static String abbreviate(String text) {
        return text.length() <= 300 ? text : text.substring(0, 300) + "...";
    }
}
