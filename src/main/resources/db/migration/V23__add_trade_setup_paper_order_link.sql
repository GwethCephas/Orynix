-- Records the paper-trading order/account a trade setup was practiced through (CLAUDE.md Section
-- 21's Integration C). Both nullable - practicing in paper trading is always optional. ON DELETE
-- SET NULL on both FKs, matching backtest_run_id's precedent above: losing the referenced paper
-- order/account must never delete the setup, only the provenance link to it.
ALTER TABLE trade_setups ADD COLUMN paper_order_id UUID REFERENCES paper_orders (id) ON DELETE SET NULL;
ALTER TABLE trade_setups ADD COLUMN paper_account_id UUID REFERENCES paper_trading_accounts (id) ON DELETE SET NULL;
