-- Unique constraint doubles as the only needed index, matching market_data_candles' precedent:
-- the sole read pattern is "all points for one run, in sequence order."
CREATE TABLE backtest_equity_points (
    id UUID PRIMARY KEY,
    backtest_run_id UUID NOT NULL REFERENCES backtest_runs (id) ON DELETE CASCADE,
    sequence_number INTEGER NOT NULL,
    timestamp TIMESTAMPTZ NOT NULL,
    cash NUMERIC(20, 4) NOT NULL,
    equity NUMERIC(20, 4) NOT NULL,
    CONSTRAINT uq_backtest_equity_points_run_sequence UNIQUE (backtest_run_id, sequence_number)
);
