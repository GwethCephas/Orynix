package com.cephCoding.orynix.backtesting.domain

import java.math.BigDecimal
import java.time.Instant

/**
 * Always fully closed — entry and exit are both populated unconditionally. A position still open
 * at the final simulated candle is force-closed before the run completes (see BacktestEngine), so
 * there is no open/dangling trade state to model here.
 */
data class Trade(
    val sequenceNumber: Int,
    val direction: TradeDirection,
    val entryTimestamp: Instant,
    val entryPrice: BigDecimal,
    val exitTimestamp: Instant,
    val exitPrice: BigDecimal,
    val quantity: BigDecimal,
    val commission: BigDecimal,
    val grossPnl: BigDecimal,
    val netPnl: BigDecimal,
    val returnPercent: BigDecimal,
    val exitReason: ExitReason,
    // Null unless the owning run had ATR risk management enabled. finalStopPrice > initialStopPrice
    // means the trailing stop ratcheted up at least once before this trade closed (see ExitReason).
    val initialStopPrice: BigDecimal? = null,
    val finalStopPrice: BigDecimal? = null,
)
