package com.rmrx.marketanalyzer.marketdata.aggregator;

import com.rmrx.marketanalyzer.marketdata.aggregator.model.Candle;
import com.rmrx.marketanalyzer.marketdata.aggregator.model.CandleUpdate;
import com.rmrx.marketanalyzer.marketdata.aggregator.serde.CandleDeserializationSchema;
import com.rmrx.marketanalyzer.marketdata.aggregator.serde.CandleUpdateSerialization;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.connector.base.DeliveryGuarantee;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.util.ParameterTool;
import org.apache.kafka.clients.consumer.OffsetResetStrategy;

/**
 * Kafka deployment of {@link CandlePipeline}: reads {@code m1-live}, {@code m1-reconciled}, {@code h1-reconciled} and
 * publishes derived candles to {@code candles-derived}. Configuration comes from program arguments,
 * default broker {@code market-analyzer-kafka:9092} (the .infra compose network).
 */
public final class CandleAggregationJob {

    private CandleAggregationJob() {
    }

    public static void main(String[] args) throws Exception {
        ParameterTool params = ParameterTool.fromArgs(args);
        String bootstrap = params.get("kafka.bootstrap-servers", "localhost:9892");
        String groupId = params.get("kafka.group-id", "candle-aggregator");
        String outputTopic = params.get("topic.output", "candles-derived");
        long checkpointMs = params.getLong("checkpoint.interval-ms", 60_000);

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.enableCheckpointing(checkpointMs);

        DataStream<CandleUpdate> derived = CandlePipeline.build(
                source(env, bootstrap, groupId, params.get("topic.m1-live", "m1-live")),
                source(env, bootstrap, groupId, params.get("topic.m1-reconciled", "m1-reconciled")),
                source(env, bootstrap, groupId, params.get("topic.h1-reconciled", "h1-reconciled")));

        derived.sinkTo(sink(bootstrap, outputTopic))
                .name(outputTopic).uid("sink-candles-derived");

        env.execute("candle-aggregation");
    }

    private static DataStream<Candle> source(StreamExecutionEnvironment env, String bootstrap, String groupId,
                                             String topic) {
        KafkaSource<Candle> source = KafkaSource.<Candle>builder()
                .setBootstrapServers(bootstrap)
                .setGroupId(groupId)
                .setTopics(topic)
                .setStartingOffsets(OffsetsInitializer.committedOffsets(OffsetResetStrategy.EARLIEST))
                .setValueOnlyDeserializer(new CandleDeserializationSchema())
                .build();
        return env.fromSource(source, WatermarkStrategy.noWatermarks(), topic).uid("source-" + topic);
    }

    private static KafkaSink<CandleUpdate> sink(String bootstrap, String topic) {
        return KafkaSink.<CandleUpdate>builder()
                .setBootstrapServers(bootstrap)
                .setRecordSerializer(KafkaRecordSerializationSchema.<CandleUpdate>builder()
                        .setTopic(topic)
                        .setKeySerializationSchema(CandleUpdateSerialization.key())
                        .setValueSerializationSchema(CandleUpdateSerialization.value())
                        .build())
                .setDeliveryGuarantee(DeliveryGuarantee.AT_LEAST_ONCE)
                .build();
    }
}
