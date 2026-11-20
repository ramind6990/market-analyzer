package com.rmrx.marketnexus.marketdata.service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Kafka settings of the service ({@code market-data.kafka.*} in application.yml). Broker, serializers and consumer
 * group are in {@code spring.kafka.*}.
 *
 * @param mode   {@code kafka} = real Kafka (publish, consume {@code candles-derived}, create topics);
 *               {@code logging} = no broker: only log what would be published, consume nothing (default)
 * @param topics topic names and how they are created
 */
@ConfigurationProperties("market-data.kafka")
public record CandleKafkaProperties(Mode mode, Topics topics) {

    /** Property switching between real Kafka and the logging stand-in. */
    public static final String MODE_PROPERTY = "market-data.kafka.mode";

    public CandleKafkaProperties {
        mode = mode == null ? Mode.LOGGING : mode;
        topics = topics == null ? new Topics(null, null, null, null, null, null, null) : topics;
    }

    public enum Mode { KAFKA, LOGGING }

    /**
     * Topics between this service and the Flink candle aggregator (and analysis) ({@code market-data.kafka.topics.*}).
     *
     * @param m1Live         live M1 (ingestion → Flink); default {@code m1-live}
     * @param m1Reconciled   M1 written by reconciliation (→ Flink); default {@code m1-reconciled}
     * @param h1Reconciled   H1 written by reconciliation (→ Flink H1 stage); default {@code h1-reconciled}
     * @param candlesDerived progressive M5..D1 from Flink (→ this service); default {@code candles-derived}
     * @param candleSeriesTracking changes of a series' complete history (this service → analysis);
     *                             default {@code candle-series-tracking}
     * @param partitions     partitions of topics created by the service (default 3)
     * @param replicas       replication factor of topics created by the service (default 1)
     */
    public record Topics(String m1Live, String m1Reconciled, String h1Reconciled, String candlesDerived,
                         String candleSeriesTracking, Integer partitions, Short replicas) {

        public Topics {
            m1Live = m1Live == null ? "m1-live" : m1Live;
            m1Reconciled = m1Reconciled == null ? "m1-reconciled" : m1Reconciled;
            h1Reconciled = h1Reconciled == null ? "h1-reconciled" : h1Reconciled;
            candlesDerived = candlesDerived == null ? "candles-derived" : candlesDerived;
            candleSeriesTracking = candleSeriesTracking == null ? "candle-series-tracking" : candleSeriesTracking;
            partitions = partitions == null ? 3 : partitions;
            replicas = replicas == null ? (short) 1 : replicas;
        }
    }
}
