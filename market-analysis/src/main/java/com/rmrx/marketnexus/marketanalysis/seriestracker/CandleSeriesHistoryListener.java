package com.rmrx.marketnexus.marketanalysis.seriestracker;

import com.rmrx.marketnexus.marketanalysis.job.SeriesTrigger;
import com.rmrx.marketnexus.marketanalysis.job.schedule.SeriesJobScheduler;
import com.rmrx.marketnexus.marketdata.model.Timeframe;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Receives {@link CandleSeriesHistoryChanged} messages from market-data-service
 * ({@code market-analysis.kafka.topics.candle-series-tracking}) and hands them to the {@link SeriesJobScheduler}
 * (returns at once). Offsets are committed before the job ran: a crash loses at most queued triggers, the next
 * message of the series recovers (jobs re-derive their range from the database). Unreadable messages are skipped.
 */
@Component
public class CandleSeriesHistoryListener {

    private static final Logger log = LoggerFactory.getLogger(CandleSeriesHistoryListener.class);

    private final SeriesJobScheduler scheduler;

    public CandleSeriesHistoryListener(SeriesJobScheduler scheduler) {
        this.scheduler = scheduler;
    }

    @KafkaListener(topics = "${market-analysis.kafka.topics.candle-series-tracking:candle-series-tracking}")
    public void onMessage(ConsumerRecord<String, byte[]> record) {
        CandleSeriesHistoryChanged change;
        try {
            change = CandleSeriesHistoryChangedJson.fromBytes(record.value());
        } catch (RuntimeException e) {
            log.error("Skipping unreadable message on {} (partition {}, offset {}, key {})", record.topic(),
                    record.partition(), record.offset(), record.key(), e);
            return;
        }
        scheduler.submit(SeriesTrigger.of(change));
    }

}
