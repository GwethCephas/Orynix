-- Nullable, unlike every other column on backtest_runs: runs persisted before this diagnostic
-- feature existed have no recorded value for them. This is a schema-versioning null, not the
-- domain-meaning null win_rate/profit_factor already use -- the frontend must render these as
-- "diagnostics unavailable for this run" rather than treating them as zero.
-- entries_skipped_insufficient_cash is the only skip reason the engine implements today
-- (BacktestEngine.run's step 1) -- there is deliberately no separate reasons table/column.
ALTER TABLE backtest_runs
    ADD COLUMN candles_evaluated INTEGER,
    ADD COLUMN entry_signals_generated INTEGER,
    ADD COLUMN exit_signals_generated INTEGER,
    ADD COLUMN entries_skipped_insufficient_cash INTEGER;
