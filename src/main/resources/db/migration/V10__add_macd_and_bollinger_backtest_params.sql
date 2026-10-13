-- Nullable, matching fast_period/slow_period/rsi_period/oversold_threshold/overbought_threshold's
-- existing precedent: exactly one strategy's fields are populated per row, enforced in
-- BacktestService, not a CHECK constraint. signal_period is MACD_CROSSOVER's signal EMA period
-- (fast_period/slow_period are reused as-is for MACD's fast/slow EMA periods - see
-- BacktestRequest.kt). bollinger_period/bollinger_std_dev_multiplier are BOLLINGER_BANDS' own two
-- parameters.
ALTER TABLE backtest_runs
    ADD COLUMN signal_period INTEGER,
    ADD COLUMN bollinger_period INTEGER,
    ADD COLUMN bollinger_std_dev_multiplier NUMERIC(6, 2);
