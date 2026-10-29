-- Associates a trade setup with a backtest run the user chose to link to it (CLAUDE.md Section 21's
-- Integration B). ON DELETE SET NULL, not CASCADE - matching backtest_runs.account_profile_id's
-- precedent (Section 14): losing the referenced run must never delete the setup, only the
-- provenance link to it. Nullable - linking a backtest is always optional.
ALTER TABLE trade_setups ADD COLUMN backtest_run_id UUID REFERENCES backtest_runs (id) ON DELETE SET NULL;
