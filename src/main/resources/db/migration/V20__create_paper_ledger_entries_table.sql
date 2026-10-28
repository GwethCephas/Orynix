-- Immutable, append-only record of every cash movement -- the auditable ledger backing
-- paper_trading_accounts.cash (CLAUDE.md Section 22). cash_balance_after is a denormalized
-- snapshot taken at write time, not recomputed later -- it is what makes the ledger independently
-- auditable (you can read the account's cash history without re-deriving it from order rows).
CREATE TABLE paper_ledger_entries (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES paper_trading_accounts (id) ON DELETE CASCADE,
    order_id UUID REFERENCES paper_orders (id) ON DELETE SET NULL,
    entry_type VARCHAR(20) NOT NULL,
    cash_delta NUMERIC(20, 4) NOT NULL,
    cash_balance_after NUMERIC(20, 4) NOT NULL,
    description VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_paper_ledger_entries_account_id_created_at ON paper_ledger_entries (account_id, created_at DESC);
