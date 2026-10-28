-- status is always 'FILLED' for every row this milestone ever writes -- a rejection (insufficient
-- buying power, stale price, invalid transition, etc.) is a thrown exception with nothing
-- persisted, matching every other feature in this codebase. The column exists as a
-- forward-compatible seam only, the same idiom trade_setups.status used before it had more than
-- one reachable value. idempotency_key + the unique constraint below is the race backstop for
-- concurrent duplicate submissions -- see CLAUDE.md Section 22 for the full catch-and-refetch flow,
-- since the fast-path dedup check happens before this constraint is ever at risk of firing.
CREATE TABLE paper_orders (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES paper_trading_accounts (id) ON DELETE CASCADE,
    position_id UUID REFERENCES paper_positions (id) ON DELETE SET NULL,
    symbol VARCHAR(20) NOT NULL,
    asset_type VARCHAR(10) NOT NULL,
    side VARCHAR(10) NOT NULL,
    position_effect VARCHAR(10) NOT NULL,
    requested_quantity NUMERIC(20, 8) NOT NULL,
    filled_quantity NUMERIC(20, 8) NOT NULL,
    fill_price NUMERIC(20, 4) NOT NULL,
    commission NUMERIC(10, 4) NOT NULL,
    status VARCHAR(20) NOT NULL,
    source_candle_timestamp TIMESTAMPTZ NOT NULL,
    execution_timestamp TIMESTAMPTZ NOT NULL,
    execution_assumptions TEXT NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_paper_orders_account_idempotency_key UNIQUE (account_id, idempotency_key)
);

CREATE INDEX idx_paper_orders_account_id_created_at ON paper_orders (account_id, created_at DESC);
