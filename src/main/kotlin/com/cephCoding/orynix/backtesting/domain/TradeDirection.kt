package com.cephCoding.orynix.backtesting.domain

/**
 * Single value today — only long positions are simulated (CLAUDE.md Section 18). Kept as an enum
 * rather than a bare string because it records a real executed fact, the same shape as
 * `CandleTimeframe.DAILY` being the only value of its enum.
 */
enum class TradeDirection {
    LONG,
}
