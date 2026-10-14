-- Nullable: most backtest_runs rows have no associated profile (requesting one is optional, see
-- CLAUDE.md Section 19) - this is the common/default case, not a schema-versioning null. ON DELETE
-- SET NULL (not CASCADE): archiving/deleting a profile must never retroactively delete the
-- historical backtest runs that referenced it - the run's own account_currency/initial_capital
-- columns already captured everything needed to reproduce its result, so losing the profile
-- reference loses only provenance, not correctness.
ALTER TABLE backtest_runs
    ADD COLUMN account_profile_id UUID REFERENCES trading_account_profiles (id) ON DELETE SET NULL;
