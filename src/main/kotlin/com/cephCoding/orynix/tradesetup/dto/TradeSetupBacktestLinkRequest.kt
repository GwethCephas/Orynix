package com.cephCoding.orynix.tradesetup.dto

import jakarta.validation.constraints.NotNull
import java.util.UUID

/**
 * Associates an already-run backtest with a trade setup (CLAUDE.md Section 21's Integration B).
 * This records that a strategy was historically backtested on the same symbol - it never implies
 * the backtest validated this specific setup's entry/stop/target levels, which the backtest engine
 * has no concept of at all.
 */
data class TradeSetupBacktestLinkRequest(
    @field:NotNull
    val backtestRunId: UUID,
)
