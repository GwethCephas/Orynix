package com.cephCoding.orynix.tradesetup.domain

/**
 * The first genuine multi-state status column in this codebase (CLAUDE.md Section 21) -
 * backtest_runs has no status column at all, and trading_account_profiles.archived is only a
 * 2-state boolean. Valid transitions, enforced in TradeSetupService, never by a generic
 * "set status to X" write:
 *
 *   DRAFT --decision--> ACCEPTED --closure--> CLOSED
 *     |
 *     +--decision--> REJECTED        (terminal)
 *     |
 *     +--expire----> EXPIRED         (terminal)
 *
 * REJECTED, EXPIRED, and CLOSED are all terminal. CLOSED is reachable only from ACCEPTED - there
 * is nothing to "finish tracking" on an idea that was rejected or expired without ever being
 * accepted.
 */
enum class TradeSetupStatus { DRAFT, ACCEPTED, REJECTED, EXPIRED, CLOSED }
