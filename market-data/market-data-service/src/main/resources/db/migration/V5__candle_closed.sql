-- Derived candles (M5..D1 from the Flink aggregator) are stored progressively: every update is upserted with
-- closed = false until the final one (closed = true). Stream and REST (poll) candles are always final.
ALTER TABLE candle ADD COLUMN closed boolean NOT NULL DEFAULT true;
