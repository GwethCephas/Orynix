-- fast_period/slow_period/rsi_period/oversold_threshold/overbought_threshold are nullable because
-- exactly one strategy's fields are populated per row, depending on strategy_type; enforced in
-- BacktestService (InvalidStrategyParametersException), not a CHECK constraint -- no existing
-- migration in this schema uses CHECK constraints for business rules.
-- No status/error_message columns: a row is written only after the simulation computes
-- successfully end to end, mirroring news_sentiments' "computed once, never updated" precedent --
-- a run that fails validation or data-fetch never reaches persistence (fails the request outright,
-- like every other feature), and completed_at marks that single moment.
-- win_rate/profit_factor are nullable for a domain reason (undefined with zero closed trades /
-- zero gross loss), not a lifecycle reason.
CREATE TABLE backtest_runs (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    symbol VARCHAR(20) NOT NULL,
    asset_type VARCHAR(10) NOT NULL,
    timeframe VARCHAR(10) NOT NULL,
    from_date DATE NOT NULL,
    to_date DATE NOT NULL,
    strategy_type VARCHAR(30) NOT NULL,
    fast_period INTEGER,
    slow_period INTEGER,
    rsi_period INTEGER,
    oversold_threshold NUMERIC(6, 2),
    overbought_threshold NUMERIC(6, 2),
    direction VARCHAR(10) NOT NULL,
    initial_capital NUMERIC(20, 4) NOT NULL,
    account_currency VARCHAR(3) NOT NULL,
    position_sizing_type VARCHAR(20) NOT NULL,
    quantity NUMERIC(20, 8) NOT NULL,
    commission_per_order NUMERIC(10, 4) NOT NULL,
    slippage_bps INTEGER NOT NULL,
    completed_at TIMESTAMPTZ NOT NULL,
    total_trades INTEGER NOT NULL,
    winning_trades INTEGER NOT NULL,
    losing_trades INTEGER NOT NULL,
    breakeven_trades INTEGER NOT NULL,
    win_rate NUMERIC(7, 4),
    profit_factor NUMERIC(10, 4),
    net_pnl NUMERIC(20, 4) NOT NULL,
    net_return_percent NUMERIC(10, 4) NOT NULL,
    max_drawdown_percent NUMERIC(10, 4) NOT NULL,
    final_equity NUMERIC(20, 4) NOT NULL
);

-- Mirrors refresh_tokens' user-id index precedent: the FK has no unique constraint of its own, and
-- the read pattern ("this user's runs, most recent first") needs it.
CREATE INDEX idx_backtest_runs_user_id_completed_at ON backtest_runs (user_id, completed_at);
