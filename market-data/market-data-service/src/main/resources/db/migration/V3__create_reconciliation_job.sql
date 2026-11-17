-- Lifecycle of a reconciliation job.
--   PENDING  waiting to run          RUNNING  being executed by a worker
--   DONE     window fully fetched     FAILED   gave up after retries
--   SKIPPED  window no longer fetchable (e.g. older than the exchange keeps M1)
CREATE TYPE job_status AS ENUM ('PENDING', 'RUNNING', 'DONE', 'FAILED', 'SKIPPED');

-- One job = one window of candles to (re)fetch from the exchange REST API for one series.
-- A window may be larger than one REST call; the worker fetches it in chunks and moves cursor_at forward.
CREATE TABLE reconciliation_job (
    id          bigserial     PRIMARY KEY,
    exchange    exchange_type NOT NULL,
    symbol      text          NOT NULL,          -- 'BASE/QUOTE', e.g. 'BTC/USDT'
    timeframe   text          NOT NULL,          -- 'M1', 'H1', ...
    window_from timestamptz   NOT NULL,          -- first open time to fetch (inclusive)
    window_to   timestamptz   NOT NULL,          -- last open time to fetch (inclusive)
    cursor_at   timestamptz   NOT NULL,          -- next open time to fetch: starts at window_from, moved forward
                                                 -- by the worker after each chunk; DONE once past window_to
    status      job_status    NOT NULL DEFAULT 'PENDING',
    attempts    int           NOT NULL DEFAULT 0,
    next_run_at timestamptz   NOT NULL DEFAULT now(),
    last_error  text,
    created_at  timestamptz   NOT NULL DEFAULT now(),
    updated_at  timestamptz   NOT NULL DEFAULT now(),
    CHECK (window_to >= window_from)
);

-- Open jobs per series: used to avoid registering the same window twice.
CREATE INDEX reconciliation_job_open ON reconciliation_job (exchange, timeframe, symbol)
    WHERE status IN ('PENDING', 'RUNNING');
