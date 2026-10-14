-- Manually-entered trading account profiles (CLAUDE.md Section 19). balance is user-maintained and
-- NEVER broker-verified -- there is no broker integration anywhere in this codebase, so this column
-- is deliberately just a plain NUMERIC the user types in, not a computed or synced value. archived
-- (not a hard DELETE) preserves profiles already referenced by past backtest_runs.account_profile_id
-- rows (V15) -- a hard delete would either cascade-null historical runs' provenance or require
-- blocking deletion entirely; archiving sidesteps both.
-- risk_percent_per_trade is NOT NULL with no DB default -- the 1% default on new profiles is applied
-- in TradingAccountService at construction time (an explicit, documented service-layer default,
-- matching DEFAULT_ATR_PERIOD/DEFAULT_INITIAL_STOP_MULTIPLIER's precedent in BacktestService), not a
-- DB-side default -- every row always has a real, intentional value either way.
-- max_daily_loss_percent / max_concurrent_positions are nullable and optional by design, not by
-- schema-versioning accident -- see CLAUDE.md Section 19 for why these are validated and stored but
-- not actively enforced in backtests today.
CREATE TABLE trading_account_profiles (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    platform VARCHAR(30) NOT NULL,
    platform_details VARCHAR(255),
    account_currency VARCHAR(3) NOT NULL,
    balance NUMERIC(20, 4) NOT NULL,
    risk_percent_per_trade NUMERIC(6, 2) NOT NULL,
    max_daily_loss_percent NUMERIC(6, 2),
    max_concurrent_positions INTEGER,
    archived BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

-- Read pattern is always "this user's non-archived profiles" (list endpoint) or "this user's one
-- profile by id" (served by the PK) -- mirrors backtest_runs' idx_backtest_runs_user_id_completed_at
-- precedent of indexing exactly the columns the actual query filters/sorts on.
CREATE INDEX idx_trading_account_profiles_user_id_archived ON trading_account_profiles (user_id, archived);
