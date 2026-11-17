-- One row per series (exchange, symbol, timeframe): facts ABOUT the candles, not the candles themselves.
CREATE TABLE market_series (
    exchange               exchange_type NOT NULL,
    symbol                 text          NOT NULL,     -- 'BASE/QUOTE', e.g. 'BTC/USDT'
    timeframe              text          NOT NULL,     -- 'M1', 'H1', ...
    -- open_time up to which the series has been verified gap-free (inclusive); NULL = never checked
    verification_watermark timestamptz,
    last_checked_at        timestamptz,
    created_at             timestamptz   NOT NULL DEFAULT now(),
    updated_at             timestamptz   NOT NULL DEFAULT now(),
    PRIMARY KEY (exchange, symbol, timeframe)
);
