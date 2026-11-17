-- Exchanges known to the system (mirrors com.rmrx.marketnexus.exchange.model.ExchangeType).
CREATE TYPE exchange_type AS ENUM ('CRYPTO_NOBITEX');

-- Where a candle came from: the live stream, a batch fetch (reconciliation over the REST API),
-- or derived from smaller candles.
CREATE TYPE candle_source AS ENUM ('STREAM', 'POLL', 'DERIVED');

-- Candles of every exchange, symbol and timeframe. Identity: (exchange, symbol, timeframe, open_time).
-- Plain PostgreSQL; the TimescaleDB hypertable/compression setup comes in a later migration.
CREATE TABLE candle (
    exchange    exchange_type NOT NULL,
    symbol      text          NOT NULL,            -- 'BASE/QUOTE', e.g. 'BTC/USDT'
    timeframe   text          NOT NULL,            -- 'M1', 'M5', 'H1', ...
    open_time   timestamptz   NOT NULL,
    open        numeric       NOT NULL,
    high        numeric       NOT NULL,
    low         numeric       NOT NULL,
    close       numeric       NOT NULL,
    volume      numeric       NOT NULL,
    source      candle_source NOT NULL,
    created_at  timestamptz   NOT NULL DEFAULT now(),
    updated_at  timestamptz   NOT NULL DEFAULT now(),
    PRIMARY KEY (exchange, symbol, timeframe, open_time)
);
