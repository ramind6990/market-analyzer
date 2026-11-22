-- Indicator values per bar and series, owned by market-analysis (schema "analysis"; market-data owns "public").
-- node_values: every node output (public and internal seed state) in the order of the series' IndicatorLayout;
-- rows of another layout_version are ignored as seeds.
CREATE TABLE indicator_value (
    exchange       text        NOT NULL,
    symbol         text        NOT NULL,
    timeframe      text        NOT NULL,
    open_time      timestamptz NOT NULL,
    layout_version bigint      NOT NULL,
    node_values    float8[]    NOT NULL,
    updated_at     timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (exchange, symbol, timeframe, open_time)
);
