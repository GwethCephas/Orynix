package com.cephCoding.orynix.tradesetup.dto

import com.cephCoding.orynix.instrument.domain.AssetType
import com.cephCoding.orynix.tradesetup.domain.TradeDirection
import com.cephCoding.orynix.tradesetup.domain.TradeSetup
import com.cephCoding.orynix.tradesetup.domain.TradeSetupSource
import com.cephCoding.orynix.tradesetup.domain.TradeSetupStatus
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/** Lighter list-view projection, mirrors BacktestSummaryResponse's precedent of a narrower list shape vs. the full detail response. */
data class TradeSetupSummaryResponse(
    val id: UUID,
    val symbol: String,
    val assetType: AssetType,
    val direction: TradeDirection,
    val entryPrice: BigDecimal,
    val stopLossPrice: BigDecimal?,
    val targetPrice: BigDecimal?,
    val source: TradeSetupSource,
    val aiProposalId: UUID?,
    val backtestRunId: UUID?,
    val paperOrderId: UUID?,
    val status: TradeSetupStatus,
    val createdAt: Instant,
) {
    companion object {
        fun from(setup: TradeSetup) = TradeSetupSummaryResponse(
            id = setup.id,
            symbol = setup.symbol,
            assetType = setup.assetType,
            direction = setup.direction,
            entryPrice = setup.entryPrice,
            stopLossPrice = setup.stopLossPrice,
            targetPrice = setup.targetPrice,
            source = setup.source,
            aiProposalId = setup.aiProposalId,
            backtestRunId = setup.backtestRunId,
            paperOrderId = setup.paperOrderId,
            status = setup.status,
            createdAt = setup.createdAt,
        )
    }
}
