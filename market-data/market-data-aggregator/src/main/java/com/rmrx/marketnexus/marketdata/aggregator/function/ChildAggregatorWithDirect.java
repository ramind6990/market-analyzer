package com.rmrx.marketnexus.marketdata.aggregator.function;

import com.rmrx.marketnexus.marketdata.aggregator.model.CandleUpdate;
import com.rmrx.marketnexus.marketdata.aggregator.model.Timeframe;
import org.apache.flink.api.common.functions.OpenContext;
import org.apache.flink.streaming.api.functions.co.KeyedCoProcessFunction;
import org.apache.flink.util.Collector;

/**
 * H1 stage: input 1 = M30 from the cascade, input 2 = authoritative H1 from REST reconciliation.
 * Reconciled M30 is ignored — an hour with a gap is completed by its REST H1, which the reconciler always sends.
 */
public class ChildAggregatorWithDirect extends KeyedCoProcessFunction<String, CandleUpdate, CandleUpdate, CandleUpdate> {

    private final Timeframe timeframe;
    private transient WindowBook book;

    public ChildAggregatorWithDirect(Timeframe timeframe) {
        this.timeframe = timeframe;
    }

    @Override
    public void open(OpenContext openContext) {
        book = new WindowBook(timeframe, getRuntimeContext());
    }

    @Override
    public void processElement1(CandleUpdate child, Context ctx, Collector<CandleUpdate> out) throws Exception {
        if (!child.reconciled()) {
            book.onChild(child, ctx.timerService(), out);
        }
    }

    @Override
    public void processElement2(CandleUpdate direct, Context ctx, Collector<CandleUpdate> out) throws Exception {
        book.onDirect(direct, out);
    }

    @Override
    public void onTimer(long timestamp, OnTimerContext ctx, Collector<CandleUpdate> out) throws Exception {
        book.onTimer(timestamp);
    }
}
