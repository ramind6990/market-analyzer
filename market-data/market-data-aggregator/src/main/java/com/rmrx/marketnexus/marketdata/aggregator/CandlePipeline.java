package com.rmrx.marketnexus.marketdata.aggregator;

import com.rmrx.marketnexus.marketdata.aggregator.function.ChildAggregator;
import com.rmrx.marketnexus.marketdata.aggregator.function.ChildAggregatorWithDirect;
import com.rmrx.marketnexus.marketdata.aggregator.model.Candle;
import com.rmrx.marketnexus.marketdata.aggregator.model.CandleUpdate;
import com.rmrx.marketnexus.marketdata.aggregator.model.Timeframe;
import org.apache.flink.streaming.api.datastream.DataStream;

/**
 * The aggregation cascade, independent of sources and sinks:
 * <pre>
 * m1-live -------+
 * m1-reconciled -+-> [M5] -> [M15] -> [M30] -> [H1] -> [H4] -> [H12] -> [D1]
 *                                               ^
 *                           h1-reconciled ------+
 * </pre>
 * Returns all derived candles (M5..D1), emitted progressively. REST H1 feeds H4 but is not part of the output.
 */
public final class CandlePipeline {

    private CandlePipeline() {
    }

    public static DataStream<CandleUpdate> build(DataStream<Candle> m1Live, DataStream<Candle> m1Reconciled,
                                                 DataStream<Candle> h1Reconciled) {
        DataStream<CandleUpdate> m1 = wrap(m1Live, "m1-live", false)
                .union(wrap(m1Reconciled, "m1-reconciled", true));

        DataStream<CandleUpdate> m5 = derive(m1, Timeframe.M5);
        DataStream<CandleUpdate> m15 = derive(m5, Timeframe.M15);
        DataStream<CandleUpdate> m30 = derive(m15, Timeframe.M30);
        DataStream<CandleUpdate> h1 = m30.keyBy(CandleUpdate::marketKey)
                .connect(wrap(h1Reconciled, "h1-reconciled", true).keyBy(CandleUpdate::marketKey))
                .process(new ChildAggregatorWithDirect(Timeframe.H1))
                .name("H1").uid("aggregate-H1");
        DataStream<CandleUpdate> h4 = derive(h1, Timeframe.H4);
        DataStream<CandleUpdate> h12 = derive(h4, Timeframe.H12);
        DataStream<CandleUpdate> d1 = derive(h12, Timeframe.D1);

        // REST H1 is already persisted by reconciliation: it flows on to H4 but is not re-published.
        // Every reconciled H1 leaving the H1 stage is a REST H1 (the stage ignores reconciled M30).
        DataStream<CandleUpdate> h1Derived = h1.filter(candle -> !candle.reconciled())
                .name("H1-derived-only").uid("filter-H1-derived");

        return m5.union(m15, m30, h1Derived, h4, h12, d1);
    }

    private static DataStream<CandleUpdate> wrap(DataStream<Candle> input, String name, boolean reconciled) {
        return input.map(candle -> CandleUpdate.closedFrom(candle, reconciled))
                .name("wrap-" + name).uid("wrap-" + name);
    }

    private static DataStream<CandleUpdate> derive(DataStream<CandleUpdate> children, Timeframe timeframe) {
        return children.keyBy(CandleUpdate::marketKey)
                .process(new ChildAggregator(timeframe))
                .name(timeframe.name()).uid("aggregate-" + timeframe);
    }
}
