CREATE TABLE ai_trade_setup_proposals (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    asset_type VARCHAR(10) NOT NULL,
    symbol VARCHAR(20) NOT NULL,
    direction VARCHAR(10) NOT NULL,
    entry_price NUMERIC(20, 4) NOT NULL,
    stop_loss_price NUMERIC(20, 4) NOT NULL,
    take_profit_price NUMERIC(20, 4),
    rationale TEXT NOT NULL,
    provider_name VARCHAR(50) NOT NULL,
    model VARCHAR(50) NOT NULL,
    analysis_as_of TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

-- Dominant read pattern: a single ownership-checked point lookup by id when a trade setup is
-- created from a proposal (findByIdAndUserId) - no separate index needed, matching
-- news_sentiments' precedent of relying on the PK alone when there is no secondary read pattern.
CREATE INDEX idx_ai_trade_setup_proposals_user_id ON ai_trade_setup_proposals (user_id);

-- A verifiable link from a trade setup back to the server-persisted AI proposal it came from.
-- Nullable (manual setups never set it) and UNIQUE (Postgres permits multiple NULLs under a plain
-- UNIQUE constraint, so manual setups are unaffected) - this is what prevents the same proposal
-- backing two separate trade setups.
ALTER TABLE trade_setups ADD COLUMN ai_proposal_id UUID REFERENCES ai_trade_setup_proposals (id) ON DELETE SET NULL;
ALTER TABLE trade_setups ADD CONSTRAINT uq_trade_setups_ai_proposal_id UNIQUE (ai_proposal_id);
