-- Names of the values in indicator_value.node_values: entries[i] names node_values[i] (Postgres arrays are 1-based,
-- both columns alike). Entry = canonical indicator output, e.g. 'RSI(period=14,source=CLOSE).value'; internal seed
-- state too, e.g. 'RSI(period=14,source=CLOSE).avgGain'. layout_version = hash of the ordered entries.
-- Example: SELECT v.node_values[array_position(l.entries, 'RSI(period=14,source=CLOSE).value')]
--          FROM indicator_value v JOIN indicator_layout l USING (layout_version) ...
CREATE TABLE indicator_layout (
    layout_version bigint      PRIMARY KEY,
    entries        text[]      NOT NULL,
    created_at     timestamptz NOT NULL DEFAULT now()
);
