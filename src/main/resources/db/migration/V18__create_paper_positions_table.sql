-- One open position per (account, symbol), enforced here as the DB-level backstop and in
-- PaperTradingOrderService as the actual gate (rejecting an opposite-direction open before this
-- constraint would ever fire). total_open_commission_paid/realized_pnl accumulate across the
-- position's life (adds and partial closes) -- see CLAUDE.md Section 22 for the full reconciliation
-- identity these two columns feed into. stop_loss_price/take_profit_price are stored and validated
-- but never automatically monitored in this milestone -- manual close only, matching
-- trading_account_profiles.max_daily_loss_percent's "stored, validated, not enforced" precedent.
CREATE TABLE paper_positions (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES paper_trading_accounts (id) ON DELETE CASCADE,
    symbol VARCHAR(20) NOT NULL,
    asset_type VARCHAR(10) NOT NULL,
    direction VARCHAR(10) NOT NULL,
    status VARCHAR(10) NOT NULL,
    open_quantity NUMERIC(20, 8) NOT NULL,
    avg_entry_price NUMERIC(20, 4) NOT NULL,
    total_open_commission_paid NUMERIC(10, 4) NOT NULL DEFAULT 0,
    realized_pnl NUMERIC(20, 4) NOT NULL DEFAULT 0,
    stop_loss_price NUMERIC(20, 4),
    take_profit_price NUMERIC(20, 4),
    opened_at TIMESTAMPTZ NOT NULL,
    closed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX uq_paper_positions_account_symbol_open ON paper_positions (account_id, symbol) WHERE status = 'OPEN';
CREATE INDEX idx_paper_positions_account_id_status ON paper_positions (account_id, status);
