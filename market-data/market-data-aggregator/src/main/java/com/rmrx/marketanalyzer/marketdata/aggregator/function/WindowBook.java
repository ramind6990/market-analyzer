package com.rmrx.marketanalyzer.marketdata.aggregator.function;

import com.rmrx.marketanalyzer.marketdata.aggregator.model.CandleUpdate;
import com.rmrx.marketanalyzer.marketdata.aggregator.model.Timeframe;
import org.apache.flink.api.common.functions.RuntimeContext;
import org.apache.flink.api.common.state.MapState;
import org.apache.flink.api.common.state.MapStateDescriptor;
import org.apache.flink.api.common.state.StateTtlConfig;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.streaming.api.TimerService;
import org.apache.flink.util.Collector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Keyed state and count-complete window logic of one cascade stage (per market key).
 * <ul>
 *   <li>children are stored by openTime, the latest version wins (idempotent for duplicates);</li>
 *   <li>every child update emits the parent progressively ({@code closed=false});</li>
 *   <li>the parent closes when the number of closed children equals {@link Timeframe#expectedChildren()};</li>
 *   <li>several windows may be open at once; incomplete ones are purged at {@code windowStart + 7 days};</li>
 *   <li>children arriving for an already closed window are dropped (closed windows are remembered with a TTL).</li>
 * </ul>
 */
final class WindowBook {

    private static final Logger LOG = LoggerFactory.getLogger(WindowBook.class);

    /** Incomplete windows are dropped this long after their start (Nobitex keeps M1 for ~7 days). */
    static final Duration PURGE_AFTER = Duration.ofDays(7);
    /** How long closed window starts are remembered to reject late children. */
    static final Duration CLOSED_TTL = Duration.ofDays(2);

    private final Timeframe timeframe;
    private final MapState<Long, CandleUpdate> children;
    private final MapState<Long, Boolean> closedWindows;

    WindowBook(Timeframe timeframe, RuntimeContext context) {
        this.timeframe = timeframe;
        this.children = context.getMapState(new MapStateDescriptor<>(
                "children-" + timeframe, Types.LONG, TypeInformation.of(CandleUpdate.class)));
        MapStateDescriptor<Long, Boolean> closedDescriptor = new MapStateDescriptor<>(
                "closed-" + timeframe, Types.LONG, Types.BOOLEAN);
        closedDescriptor.enableTimeToLive(StateTtlConfig.newBuilder(CLOSED_TTL).build());
        this.closedWindows = context.getMapState(closedDescriptor);
    }

    /** Adds or replaces a child and emits the parent (progressive or closed). */
    void onChild(CandleUpdate child, TimerService timers, Collector<CandleUpdate> out) throws Exception {
        long start = timeframe.windowStart(child.exchange(), child.openTime());
        if (closedWindows.contains(start)) {
            LOG.info("Dropping late {} child {} for closed {} window {}", child.timeframe(), child.openTime(),
                    timeframe, start);
            return;
        }
        children.put(child.openTime().toEpochMilli(), child);
        timers.registerProcessingTimeTimer(start + PURGE_AFTER.toMillis());

        List<CandleUpdate> window = window(start);
        long closedCount = window.stream().filter(CandleUpdate::closed).count();
        boolean complete = closedCount >= timeframe.expectedChildren();
        out.collect(CandleMerger.merge(timeframe, start, window, complete));
        if (complete) {
            close(start);
        }
    }

    /** Emits an authoritative closed candle (REST H1) for its window, replacing whatever was collected. */
    void onDirect(CandleUpdate direct, Collector<CandleUpdate> out) throws Exception {
        long start = timeframe.windowStart(direct.exchange(), direct.openTime());
        out.collect(direct);
        close(start);
    }

    /** Purges windows whose purge time has passed. */
    void onTimer(long timestamp) throws Exception {
        long cutoff = timestamp - PURGE_AFTER.toMillis();
        Iterator<Map.Entry<Long, CandleUpdate>> it = children.iterator();
        while (it.hasNext()) {
            Map.Entry<Long, CandleUpdate> entry = it.next();
            long start = timeframe.windowStart(entry.getValue().exchange(), entry.getValue().openTime());
            if (start <= cutoff) {
                CandleUpdate c = entry.getValue();
                LOG.warn("{} window {} of {} never completed; dropping child {}", timeframe, start, c.marketKey(),
                        c.openTime());
                it.remove();
            }
        }
    }

    private void close(long start) throws Exception {
        long end = start + timeframe.duration().toMillis();
        Iterator<Long> it = children.keys().iterator();
        while (it.hasNext()) {
            long openTime = it.next();
            if (openTime >= start && openTime < end) {
                it.remove();
            }
        }
        closedWindows.put(start, true);
    }

    private List<CandleUpdate> window(long start) throws Exception {
        long end = start + timeframe.duration().toMillis();
        List<CandleUpdate> result = new ArrayList<>();
        for (Map.Entry<Long, CandleUpdate> entry : children.entries()) {
            if (entry.getKey() >= start && entry.getKey() < end) {
                result.add(entry.getValue());
            }
        }
        result.sort(Comparator.comparing(CandleUpdate::openTime));
        return result;
    }
}
