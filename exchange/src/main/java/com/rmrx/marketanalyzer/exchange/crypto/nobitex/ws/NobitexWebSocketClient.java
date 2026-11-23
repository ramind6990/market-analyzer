package com.rmrx.marketanalyzer.exchange.crypto.nobitex.ws;

import io.github.centrifugal.centrifuge.Client;
import io.github.centrifugal.centrifuge.ConnectedEvent;
import io.github.centrifugal.centrifuge.ConnectingEvent;
import io.github.centrifugal.centrifuge.DisconnectedEvent;
import io.github.centrifugal.centrifuge.DuplicateSubscriptionException;
import io.github.centrifugal.centrifuge.ErrorEvent;
import io.github.centrifugal.centrifuge.EventListener;
import io.github.centrifugal.centrifuge.Options;
import io.github.centrifugal.centrifuge.PublicationEvent;
import io.github.centrifugal.centrifuge.SubscribedEvent;
import io.github.centrifugal.centrifuge.SubscribingEvent;
import io.github.centrifugal.centrifuge.Subscription;
import io.github.centrifugal.centrifuge.SubscriptionErrorEvent;
import io.github.centrifugal.centrifuge.SubscriptionEventListener;
import io.github.centrifugal.centrifuge.SubscriptionOptions;
import io.github.centrifugal.centrifuge.UnsubscribedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.rmrx.marketanalyzer.exchange.crypto.nobitex.model.NobitexCandle;
import com.rmrx.marketanalyzer.exchange.crypto.nobitex.model.NobitexResolution;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Nobitex WebSocket client built on centrifuge-java (connection, ping/pong, reconnect and resubscribe are handled
 * by the SDK). Currently supports public candle channels.
 */
public final class NobitexWebSocketClient implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(NobitexWebSocketClient.class);
    private static final long CLOSE_TIMEOUT_MS = 5000;

    private final Client client;
    private final ObjectMapper mapper = JsonMapper.builder()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    public NobitexWebSocketClient(NobitexWebSocketConfig config) {
        Objects.requireNonNull(config, "config");
        Options options = new Options();
        options.setHeaders(Map.of("User-Agent", config.userAgent()));
        config.findConnectionToken().ifPresent(options::setToken);
        this.client = new Client(config.url().toString(), options, new ConnectionListener());
    }

    /** Starts connecting; the SDK reconnects automatically with backoff after failures. */
    public void connect() {
        client.connect();
    }

    /**
     * Subscribes to {@code public:candle-{SYMBOL}-{RESOLUTION}}. Can be called before or after {@link #connect()}.
     */
    public void subscribeCandles(String symbol, NobitexResolution resolution, NobitexCandleListener listener) {
        Objects.requireNonNull(symbol, "symbol");
        Objects.requireNonNull(resolution, "resolution");
        Objects.requireNonNull(listener, "listener");
        String upperSymbol = symbol.strip().toUpperCase(Locale.ROOT);
        String channel = "public:candle-" + upperSymbol + "-" + resolution.apiValue();
        try {
            Subscription subscription = client.newSubscription(channel, new SubscriptionOptions(),
                    new CandleSubscriptionListener(upperSymbol, resolution, listener));
            subscription.subscribe();
        } catch (DuplicateSubscriptionException e) {
            throw new IllegalStateException("Already subscribed to " + channel, e);
        }
    }

    @Override
    public void close() {
        client.disconnect();
        try {
            if (!client.close(CLOSE_TIMEOUT_MS)) {
                log.warn("Nobitex WebSocket client did not close within {} ms", CLOSE_TIMEOUT_MS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static final class ConnectionListener extends EventListener {
        @Override
        public void onConnecting(Client client, ConnectingEvent event) {
            log.info("Nobitex WebSocket connecting: {} ({})", event.getReason(), event.getCode());
        }

        @Override
        public void onConnected(Client client, ConnectedEvent event) {
            log.info("Nobitex WebSocket connected, client id {}", event.getClient());
        }

        @Override
        public void onDisconnected(Client client, DisconnectedEvent event) {
            log.info("Nobitex WebSocket disconnected: {} ({})", event.getReason(), event.getCode());
        }

        @Override
        public void onError(Client client, ErrorEvent event) {
            log.warn("Nobitex WebSocket error: {}", event.getError().toString());
        }
    }

    /** Parses candle publications and detects closed candles (the previous one, once {@code t} advances). */
    private final class CandleSubscriptionListener extends SubscriptionEventListener {

        private final String symbol;
        private final NobitexResolution resolution;
        private final NobitexCandleListener listener;
        private NobitexCandle current; // only touched on the SDK's single event thread

        CandleSubscriptionListener(String symbol, NobitexResolution resolution, NobitexCandleListener listener) {
            this.symbol = symbol;
            this.resolution = resolution;
            this.listener = listener;
        }

        @Override
        public void onSubscribing(Subscription sub, SubscribingEvent event) {
            log.info("Subscribing to {}: {}", sub.getChannel(), event.getReason());
        }

        @Override
        public void onSubscribed(Subscription sub, SubscribedEvent event) {
            log.info("Subscribed to {}", sub.getChannel());
        }

        @Override
        public void onUnsubscribed(Subscription sub, UnsubscribedEvent event) {
            log.info("Unsubscribed from {}: {}", sub.getChannel(), event.getReason());
        }

        @Override
        public void onError(Subscription sub, SubscriptionErrorEvent event) {
            log.warn("Subscription error on {}: {}", sub.getChannel(), event.getError().toString());
        }

        @Override
        public void onPublication(Subscription sub, PublicationEvent event) {
            // Exceptions thrown here would make the SDK drop the connection, so handle everything locally.
            try {
                NobitexWsCandleMessage message = parse(event.getData());
                if (message == null || !message.isComplete()) {
                    log.warn("Ignoring incomplete candle message on {}", sub.getChannel());
                    return;
                }
                NobitexCandle candle = message.toCandle(symbol, resolution);
                if (current != null && candle.openTime().isAfter(current.openTime())) {
                    listener.onClosed(current);
                }
                if (current == null || !candle.openTime().isBefore(current.openTime())) {
                    current = candle;
                    listener.onUpdate(candle);
                }
            } catch (RuntimeException e) {
                log.warn("Failed to handle publication on {}", sub.getChannel(), e);
            }
        }

        /** The payload is the candle JSON object, or (per the Nobitex docs) a JSON string containing it. */
        private NobitexWsCandleMessage parse(byte[] data) {
            JsonNode node = mapper.readTree(data);
            if (node.isString()) {
                node = mapper.readTree(node.asString());
            }
            return mapper.treeToValue(node, NobitexWsCandleMessage.class);
        }
    }
}
