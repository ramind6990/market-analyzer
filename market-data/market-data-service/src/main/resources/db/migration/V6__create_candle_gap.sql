-- Gaps seen by the series tracker: written when a closed candle arrives beyond the end of the newest run,
-- marked filled once all its candles are stored. The reconciliation registrar reads the unfilled rows instead of
-- scanning the candle table. (Missing candles after the newest one - a silent feed - are not stored here: the
-- registrar derives them from the tracker.)
CREATE TABLE candle_gap (
    id          bigserial     PRIMARY KEY,
    exchange    exchange_type NOT NULL,
    symbol      text          NOT NULL,          -- 'BASE/QUOTE', e.g. 'BTC/USDT'
    timeframe   text          NOT NULL,          -- 'M1', 'H1', ...
    gap_from    timestamptz   NOT NULL,          -- open time of the first missing candle (inclusive)
    gap_to      timestamptz   NOT NULL,          -- open time of the last missing candle (inclusive)
    filled      boolean       NOT NULL DEFAULT false,
    detected_at timestamptz   NOT NULL DEFAULT now(),
    filled_at   timestamptz,
    updated_at  timestamptz   NOT NULL DEFAULT now(),
    CHECK (gap_to >= gap_from),
    UNIQUE (exchange, symbol, timeframe, gap_from)
);

-- Unfilled gaps per exchange and timeframe: what the registrar reads on every run.
CREATE INDEX candle_gap_unfilled ON candle_gap (exchange, timeframe, symbol)
    WHERE NOT filled;
