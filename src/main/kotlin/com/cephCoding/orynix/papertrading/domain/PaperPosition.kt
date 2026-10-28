package com.cephCoding.orynix.papertrading.domain

import com.cephCoding.orynix.instrument.domain.AssetType
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Price-independent, always-exact position state. `realizedPnl` accumulates only across closing
 * fills (opening commissions hit cash/equity immediately at open time instead - see the
 * reconciliation identity in CLAUDE.md Section 22, which depends on this exact split).
 * `stopLossPrice`/`takeProfitPrice` are stored and validated but never automatically monitored in
 * this milestone - manual close only.
 */
data class PaperPosition(
    val id: UUID,
    val symbol: String,
    val assetType: AssetType,
    val direction: PositionDirection,
    val status: PositionStatus,
    val openQuantity: BigDecimal,
    val avgEntryPrice: BigDecimal,
    val totalOpenCommissionPaid: BigDecimal,
    val realizedPnl: BigDecimal,
    val stopLossPrice: BigDecimal?,
    val takeProfitPrice: BigDecimal?,
    val openedAt: Instant,
    val closedAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
)
