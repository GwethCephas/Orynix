CREATE TABLE trade_setups (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    asset_type VARCHAR(10) NOT NULL,
    symbol VARCHAR(20) NOT NULL,
    direction VARCHAR(10) NOT NULL,
    entry_price NUMERIC(20, 4) NOT NULL,
    stop_loss_price NUMERIC(20, 4),
    target_price NUMERIC(20, 4),
    rationale TEXT,
    source VARCHAR(20) NOT NULL,
    ai_provider_name VARCHAR(50),
    ai_model VARCHAR(50),
    ai_analysis_as_of TIMESTAMPTZ,
    account_profile_id UUID REFERENCES trading_account_profiles (id) ON DELETE SET NULL,
    status VARCHAR(20) NOT NULL,
    decision_notes TEXT,
    decided_at TIMESTAMPTZ,
    exit_price NUMERIC(20, 4),
    outcome VARCHAR(20),
    closure_notes TEXT,
    closed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

-- Dominant read pattern: this user's setups, newest first, optionally narrowed by status/
-- assetType/symbol applied as plain WHERE clauses with no dedicated index of their own (small
-- per-user dataset, no speculative indexing - mirrors trading_account_profiles' precedent of
-- indexing exactly the columns the actual query filters/sorts on).
CREATE INDEX idx_trade_setups_user_id_created_at ON trade_setups (user_id, created_at DESC);
