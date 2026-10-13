-- Nullable, matching every other strategy-parameter column's existing precedent: exactly one
-- strategy's fields are populated per row, enforced in BacktestService, not a CHECK constraint.
-- donchian_entry_period/donchian_exit_period are DONCHIAN_CHANNEL_BREAKOUT's own two lookback
-- windows. adx_period/adx_threshold are ADX_TREND's own two parameters.
ALTER TABLE backtest_runs
    ADD COLUMN donchian_entry_period INTEGER,
    ADD COLUMN donchian_exit_period INTEGER,
    ADD COLUMN adx_period INTEGER,
    ADD COLUMN adx_threshold NUMERIC(6, 2);
