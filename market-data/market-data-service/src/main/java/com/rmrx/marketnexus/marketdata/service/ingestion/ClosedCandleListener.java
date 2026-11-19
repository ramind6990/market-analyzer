package com.rmrx.marketnexus.marketdata.service.ingestion;

import com.rmrx.marketnexus.marketdata.model.Candle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Called once a derived candle is stored in its final ({@code closed}) version. Partial updates never reach it.
 * <p>
 * LOGGING ONLY FOR NOW: later this notifies analysis (CandlesClosed events for subscribed, LIVE series).
 */
@Component
public class ClosedCandleListener {

    private static final Logger log = LoggerFactory.getLogger(ClosedCandleListener.class);

    public void onClosed(Candle candle, boolean reconciled) {
        log.info("Closed {} {} {}{} (analysis notification not implemented yet)", candle.marketKey(),
                candle.timeframe(), candle.openTime(), reconciled ? " [reconciled]" : "");
    }
}
