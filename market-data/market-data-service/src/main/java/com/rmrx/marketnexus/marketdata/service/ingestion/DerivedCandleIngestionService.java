package com.rmrx.marketnexus.marketdata.service.ingestion;

import com.rmrx.marketnexus.marketdata.service.candle.CandleRepository;
import com.rmrx.marketnexus.marketdata.service.candle.DerivedCandle;
import com.rmrx.marketnexus.marketdata.service.config.CandleKafkaProperties;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Stores the derived candles (M5..D1) the Flink aggregator publishes on {@code market-data.kafka.topics.candles-derived}.
 * <p>
 * Batch listener: each poll's records (bounded by {@code spring.kafka.consumer.fetch-min-size / fetch-max-wait /
 * max-poll-records}, i.e. "enough messages or enough time") are upserted in ONE statement (source DERIVED): partial
 * updates with {@code closed = false}, final ones with {@code closed = true}; only the newest update per candle is
 * written, and a partial update never overwrites a closed candle. Offsets are committed after the batch is stored,
 * so a crash loses nothing (the upsert is idempotent on redelivery).
 * <p>
 * Final candles that changed the stored row are handed to {@link ClosedCandleListener}, in open-time order.
 * Active in kafka mode only.
 */
@Service
@ConditionalOnProperty(name = CandleKafkaProperties.MODE_PROPERTY, havingValue = "kafka")
public class DerivedCandleIngestionService {

    private static final Logger log = LoggerFactory.getLogger(DerivedCandleIngestionService.class);

    private final CandleRepository repository;
    private final ClosedCandleListener closedListener;

    public DerivedCandleIngestionService(CandleRepository repository, ClosedCandleListener closedListener) {
        this.repository = repository;
        this.closedListener = closedListener;
    }

    @KafkaListener(topics = "${market-data.kafka.topics.candles-derived:candles-derived}", batch = "true")
    public void onMessages(List<ConsumerRecord<String, byte[]>> records) {
        List<DerivedCandleMessage> messages = new ArrayList<>(records.size());
        for (ConsumerRecord<String, byte[]> record : records) {
            try {
                messages.add(DerivedCandleMessage.fromBytes(record.value()));
            } catch (RuntimeException e) {
                log.error("Skipping unreadable message on {} (partition {}, offset {}, key {})", record.topic(),
                        record.partition(), record.offset(), record.key(), e);
            }
        }
        ingest(messages);
    }

    void ingest(List<DerivedCandleMessage> messages) {
        List<DerivedCandle> candles = new ArrayList<>(messages.size());
        for (DerivedCandleMessage message : messages) {
            if (message.candle().isConsistent()) {
                candles.add(new DerivedCandle(message.candle(), message.closed()));
            } else {
                log.warn("Rejecting inconsistent derived candle {}", message);
            }
        }
        // A database failure propagates: the container redelivers the batch (default error handler), then logs it.
        List<DerivedCandle> written = repository.upsertDerivedAll(candles);
        log.debug("Derived batch: {} message(s), {} row(s) written", messages.size(), written.size());

        Map<DerivedCandle, Boolean> reconciled = messages.stream().collect(Collectors.toMap(
                message -> new DerivedCandle(message.candle(), message.closed()), DerivedCandleMessage::reconciled,
                (first, last) -> last));
        written.stream()
                .filter(DerivedCandle::closed)
                .sorted(Comparator.comparing(candle -> candle.candle().openTime()))
                .forEach(candle -> closedListener.onClosed(candle.candle(), reconciled.getOrDefault(candle, false)));
    }
}
