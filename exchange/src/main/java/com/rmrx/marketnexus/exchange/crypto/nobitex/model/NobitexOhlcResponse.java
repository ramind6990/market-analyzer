package com.rmrx.marketnexus.exchange.crypto.nobitex.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Raw response of {@code GET /market/udf/history} (TradingView UDF format): one array per field,
 * where index {@code i} of every array belongs to the same candle.
 *
 * @param s      status: {@value #STATUS_OK}, {@value #STATUS_NO_DATA} or {@value #STATUS_ERROR}
 * @param t      candle open times, Unix seconds
 * @param o      open prices
 * @param h      high prices
 * @param l      low prices
 * @param c      close prices
 * @param v      volumes
 * @param errmsg error message when {@code s} is {@value #STATUS_ERROR}
 */
public record NobitexOhlcResponse(
        String s,
        List<Long> t,
        List<BigDecimal> o,
        List<BigDecimal> h,
        List<BigDecimal> l,
        List<BigDecimal> c,
        List<BigDecimal> v,
        String errmsg) {

    public static final String STATUS_OK = "ok";
    public static final String STATUS_NO_DATA = "no_data";
    public static final String STATUS_ERROR = "error";

    public NobitexOhlcResponse {
        t = t == null ? List.of() : List.copyOf(t);
        o = o == null ? List.of() : List.copyOf(o);
        h = h == null ? List.of() : List.copyOf(h);
        l = l == null ? List.of() : List.copyOf(l);
        c = c == null ? List.of() : List.copyOf(c);
        v = v == null ? List.of() : List.copyOf(v);
    }

    public boolean isOk() {
        return STATUS_OK.equals(s);
    }

    public boolean isNoData() {
        return STATUS_NO_DATA.equals(s);
    }

    public boolean isError() {
        return STATUS_ERROR.equals(s);
    }

    /**
     * Flattens the arrays into candles, oldest first as returned by the server.
     * Returns an empty list for {@code no_data}.
     *
     * @throws IllegalStateException when the response is an error or the arrays differ in length
     */
    public List<NobitexCandle> toCandles(String symbol, NobitexResolution resolution) {
        if (isNoData()) {
            return List.of();
        }
        if (!isOk()) {
            throw new IllegalStateException("OHLC response is not ok: s=" + s + ", errmsg=" + errmsg);
        }
        int size = t.size();
        if (o.size() != size || h.size() != size || l.size() != size || c.size() != size || v.size() != size) {
            throw new IllegalStateException("OHLC arrays differ in length: t=" + size + ", o=" + o.size()
                    + ", h=" + h.size() + ", l=" + l.size() + ", c=" + c.size() + ", v=" + v.size());
        }
        List<NobitexCandle> candles = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            candles.add(new NobitexCandle(symbol, resolution, Instant.ofEpochSecond(t.get(i)),
                    o.get(i), h.get(i), l.get(i), c.get(i), v.get(i)));
        }
        return List.copyOf(candles);
    }
}
