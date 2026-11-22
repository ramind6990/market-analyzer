-- Each layout keeps its own rows: a layout change (indicator added/removed/changed) recomputes the series into new
-- rows; the rows of the old layout stay (removed later by retention only).
ALTER TABLE indicator_value DROP CONSTRAINT indicator_value_pkey;
ALTER TABLE indicator_value ADD PRIMARY KEY (exchange, symbol, timeframe, open_time, layout_version);

-- internal[i]: node_values[i] is hidden seed state (e.g. RSI average gain), not an indicator value for consumers.
-- created_at orders layouts: for a bar stored under several layouts the newest layout is the current one.
ALTER TABLE indicator_layout ADD COLUMN internal boolean[];

-- Indicator types as known to the code, one row per formula version. Descriptive: explains layout entries
-- (TYPE or TYPE@version) and keeps the history of formula versions.
CREATE TABLE indicator_definition (
    type        text        NOT NULL,
    version     int         NOT NULL,
    family      text        NOT NULL,
    description text        NOT NULL,
    params      jsonb       NOT NULL,     -- [{name, kind, default, min, max, allowed, description}]
    outputs     jsonb       NOT NULL,     -- [{name, visibility (PUBLIC/INTERNAL), description}] in layout order
    created_at  timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (type, version)
);
