-- Paper trading (CLAUDE.md Section 22): a dedicated, isolated virtual cash account, never the
-- user's manually-entered trading_account_profiles.balance (that table remains purely descriptive
-- metadata for seeding backtests/risk calculations, never live spendable cash). status is set in
-- the service layer at construction, not a DB default, matching trading_account_profiles.archived's
-- precedent of an explicit service-layer value for every row.
CREATE TABLE paper_trading_accounts (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    base_currency VARCHAR(3) NOT NULL,
    initial_deposit NUMERIC(20, 4) NOT NULL,
    cash NUMERIC(20, 4) NOT NULL,
    -- Explicit simulated fee/slippage assumptions per account, mirroring backtest_runs'
    -- commission_per_order/slippage_bps fields exactly (same semantics, same defaults) -- every
    -- fill discloses which of these applied via PaperOrder.executionAssumptions.
    commission_per_order NUMERIC(10, 4) NOT NULL DEFAULT 0,
    slippage_bps INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

-- Dominant read pattern: this user's active accounts, mirroring trading_account_profiles'
-- idx_trading_account_profiles_user_id_archived precedent of indexing exactly the columns the
-- list endpoint filters on.
CREATE INDEX idx_paper_trading_accounts_user_id_status ON paper_trading_accounts (user_id, status);
