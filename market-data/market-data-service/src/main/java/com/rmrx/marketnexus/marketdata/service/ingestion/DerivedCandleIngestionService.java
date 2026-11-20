package com.rmrx.marketnexus.marketdata.service.ingestion;

import com.rmrx.marketnexus.marketdata.service.candle.CandleRepository;
import com.rmrx.marketnexus.marketdata.service.candle.DerivedCandle;
import com.rmrx.marketnexus.marketdata.service.config.CandleKafkaProperties;
import com.rmrx.marketnexus.marketdata.service.tracking.tracker.SeriesKey;
import com.rmrx.marketnexus.marketdata.service.tracking.tracker.SeriesTracker;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Stores the derived candles (M5..D1) the Flink aggregator publishes on {@code market-data.kafka.topics.candles-derived}.
 * <p>
 * Batch listener: each poll's records (bounded by {@code spring.kafka.consumer.fetch-min-size / fetch-max-wait /
 * max-poll-records}, i.e. "enough messages or enough time") are upserted in ONE statement (source DERIVED): partial
 * updates with {@code closed = false}, final ones with {@code closed = true}; only the newest update per candle is
 * written, and a partial update never overwrites a closed candle. Offsets are committed after the batch is stored,
 * so a crash loses nothing (the upsert is idempotent on redelivery).
 * <p>
 * Final candles that changed the stored row are handed to {@link SeriesTracker} after the commit, per series.
 * Active in kafka mode only.
 */
@Service
@ConditionalOnProperty(name = CandleKafkaProperties.MODE_PROPERTY, havingValue = "kafka")
public class DerivedCandleIngestionService {

    private static final Logger log = LoggerFactory.getLogger(DerivedCandleIngestionService.class);

    private final CandleRepository repository;
    private final SeriesTracker tracker;

    public DerivedCandleIngestionService(CandleRepository repository, SeriesTracker tracker) {
        this.repository = repository;
        this.tracker = tracker;
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

        Map<SeriesKey, List<Instant>> closedBySeries = new LinkedHashMap<>();
        written.stream()
                .filter(DerivedCandle::closed)
                .map(DerivedCandle::candle)
                .forEach(candle -> closedBySeries
                        .computeIfAbsent(new SeriesKey(candle.exchange(), candle.symbol(), candle.timeframe()),
                                key -> new ArrayList<>())
                        .add(candle.openTime()));
        closedBySeries.forEach(tracker::onClosedCandles);
    }
}
