package com.rmrx.marketanalyzer.exchange.crypto.nobitex.ws;

import com.rmrx.marketanalyzer.exchange.crypto.nobitex.model.NobitexCandle;
import com.rmrx.marketanalyzer.exchange.crypto.nobitex.model.NobitexResolution;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Payload of the {@code public:candle-{SYMBOL}-{RESOLUTION}} channel, e.g.
 * {@code {"t":1731852900,"o":6240000001.0,"h":6250000000.0,"l":6238000000.0,"c":6238031033.0,"v":1.26}}.
 * Published about every 10 seconds; it describes the candle that is still forming.
 *
 * @param t candle open time, Unix seconds
 */
public record NobitexWsCandleMessage(
        Long t,
        BigDecimal o,
        BigDecimal h,
        BigDecimal l,
        BigDecimal c,
        BigDecimal v) {

    public boolean isComplete() {
        return t != null && o != null && h != null && l != null && c != null && v != null;
    }

    public NobitexCandle toCandle(String symbol, NobitexResolution resolution) {
        return new NobitexCandle(symbol, resolution, Instant.ofEpochSecond(t), o, h, l, c, v);
    }
}
