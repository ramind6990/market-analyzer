package com.rmrx.marketanalyzer.marketdata.aggregator.function;

import com.rmrx.marketanalyzer.marketdata.aggregator.model.CandleUpdate;
import com.rmrx.marketanalyzer.marketdata.aggregator.model.Timeframe;
import org.apache.flink.api.common.functions.OpenContext;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.util.Collector;

/** Cascade stage (M5, M15, M30, H4, H12, D1): aggregates children of the timeframe below, keyed by market. */
public class ChildAggregator extends KeyedProcessFunction<String, CandleUpdate, CandleUpdate> {

    private final Timeframe timeframe;
    private transient WindowBook book;

    public ChildAggregator(Timeframe timeframe) {
        this.timeframe = timeframe;
    }

    @Override
    public void open(OpenContext openContext) {
        book = new WindowBook(timeframe, getRuntimeContext());
    }

    @Override
    public void processElement(CandleUpdate child, Context ctx, Collector<CandleUpdate> out) throws Exception {
        book.onChild(child, ctx.timerService(), out);
    }

    @Override
    public void onTimer(long timestamp, OnTimerContext ctx, Collector<CandleUpdate> out) throws Exception {
        book.onTimer(timestamp);
    }
}
