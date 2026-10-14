-- Nullable: populated only when the owning run had atr_risk_management_enabled = true; a run using
-- FIXED_QUANTITY with no risk management never maintains a stop price for its trades at all, which
-- is a domain-meaning null, not a schema-versioning one. initial_stop_price is set once at fill and
-- never changes; final_stop_price is the trailing ratchet's last value at the moment of exit -
-- comparing the two recovers whether (and how far) the trailing stop moved before the trade closed,
-- which is why ExitReason has a single ATR_STOP value rather than separate STOP_LOSS/TRAILING_STOP
-- reasons.
ALTER TABLE backtest_trades
    ADD COLUMN initial_stop_price NUMERIC(20, 4),
    ADD COLUMN final_stop_price NUMERIC(20, 4);
