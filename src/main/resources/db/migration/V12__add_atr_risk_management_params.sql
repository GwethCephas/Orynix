-- Nullable, matching every existing strategy/config-parameter column's precedent: whether these are
-- populated depends on atr_risk_management_enabled / position_sizing_type (already an existing
-- NOT NULL column from V6), enforced in BacktestService (InvalidStrategyParametersException), not a
-- CHECK constraint.
-- quantity moves from NOT NULL to nullable: under RISK_BASED_ATR sizing there is no run-level fixed
-- quantity (it is computed per trade and recorded on backtest_trades, unaffected by this migration)
-- - this is a domain-meaning null (mirrors win_rate/profit_factor), not a schema-versioning one.
ALTER TABLE backtest_runs
    ALTER COLUMN quantity DROP NOT NULL;

ALTER TABLE backtest_runs
    ADD COLUMN risk_percent_per_trade NUMERIC(6, 2),
    ADD COLUMN atr_risk_management_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN atr_period INTEGER,
    ADD COLUMN atr_initial_stop_multiplier NUMERIC(6, 2),
    ADD COLUMN atr_trailing_stop_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN atr_trailing_stop_multiplier NUMERIC(6, 2),
    ADD COLUMN entries_skipped_invalid_quantity INTEGER;
