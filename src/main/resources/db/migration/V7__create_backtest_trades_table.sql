-- Every trade is always fully closed (entry and exit both populated) -- an open position at the
-- final candle is force-closed before the run completes (see BacktestEngine), so there is no
-- dangling open-position row to model.
CREATE TABLE backtest_trades (
    id UUID PRIMARY KEY,
    backtest_run_id UUID NOT NULL REFERENCES backtest_runs (id) ON DELETE CASCADE,
    sequence_number INTEGER NOT NULL,
    direction VARCHAR(10) NOT NULL,
    entry_timestamp TIMESTAMPTZ NOT NULL,
    entry_price NUMERIC(20, 4) NOT NULL,
    exit_timestamp TIMESTAMPTZ NOT NULL,
    exit_price NUMERIC(20, 4) NOT NULL,
    quantity NUMERIC(20, 8) NOT NULL,
    commission NUMERIC(10, 4) NOT NULL,
    gross_pnl NUMERIC(20, 4) NOT NULL,
    net_pnl NUMERIC(20, 4) NOT NULL,
    return_percent NUMERIC(10, 4) NOT NULL,
    exit_reason VARCHAR(20) NOT NULL,
    CONSTRAINT uq_backtest_trades_run_sequence UNIQUE (backtest_run_id, sequence_number)
);
