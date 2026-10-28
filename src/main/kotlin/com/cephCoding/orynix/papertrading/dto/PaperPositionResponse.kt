package com.cephCoding.orynix.papertrading.dto

import com.cephCoding.orynix.instrument.domain.AssetType
import com.cephCoding.orynix.papertrading.domain.PaperPosition
import com.cephCoding.orynix.papertrading.domain.PositionDirection
import com.cephCoding.orynix.papertrading.domain.PositionStatus
import com.cephCoding.orynix.papertrading.domain.PositionValuation
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * `valuation` is only ever populated for an OPEN position (computed fresh at read time - never
 * cached, see CLAUDE.md Section 22). Closed positions have nothing left to mark, so it's null
 * rather than a stale/fabricated figure. `stopLossPrice`/`takeProfitPrice` are stored and
 * validated but never automatically monitored this milestone - manual close only.
 */
data class PaperPositionResponse(
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
    val valuation: PositionValuationResponse?,
) {
    companion object {
        fun from(position: PaperPosition, valuation: PositionValuation?) = PaperPositionResponse(
            id = position.id,
            symbol = position.symbol,
            assetType = position.assetType,
            direction = position.direction,
            status = position.status,
            openQuantity = position.openQuantity,
            avgEntryPrice = position.avgEntryPrice,
            totalOpenCommissionPaid = position.totalOpenCommissionPaid,
            realizedPnl = position.realizedPnl,
            stopLossPrice = position.stopLossPrice,
            takeProfitPrice = position.takeProfitPrice,
            openedAt = position.openedAt,
            closedAt = position.closedAt,
            valuation = valuation?.let(PositionValuationResponse::from),
        )
    }
}
