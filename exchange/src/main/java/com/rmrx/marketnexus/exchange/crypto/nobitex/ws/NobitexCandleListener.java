package com.rmrx.marketnexus.exchange.crypto.nobitex.ws;

import com.rmrx.marketnexus.exchange.crypto.nobitex.model.NobitexCandle;

/**
 * Receives candle updates from a Nobitex WebSocket candle subscription.
 * Called on the WebSocket client's single event thread: return quickly and do not block.
 */
public interface NobitexCandleListener {

    /** The current, still-forming candle (sent about every 10 seconds). */
    void onUpdate(NobitexCandle forming);

    /**
     * The previous candle, considered closed because an update for a newer candle arrived.
     * Its values are the last ones seen over the WebSocket, which may miss trades from the candle's last seconds.
     */
    void onClosed(NobitexCandle closed);
}
