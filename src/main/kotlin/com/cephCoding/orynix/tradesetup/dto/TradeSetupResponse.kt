package com.cephCoding.orynix.tradesetup.dto

import com.cephCoding.orynix.instrument.domain.AssetType
import com.cephCoding.orynix.tradesetup.domain.TradeDirection
import com.cephCoding.orynix.tradesetup.domain.TradeSetup
import com.cephCoding.orynix.tradesetup.domain.TradeSetupOutcome
import com.cephCoding.orynix.tradesetup.domain.TradeSetupSource
import com.cephCoding.orynix.tradesetup.domain.TradeSetupStatus
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class TradeSetupResponse(
    val id: UUID,
    val assetType: AssetType,
    val symbol: String,
    val direction: TradeDirection,
    val entryPrice: BigDecimal,
    val stopLossPrice: BigDecimal?,
    val targetPrice: BigDecimal?,
    val rationale: String?,
    val source: TradeSetupSource,
    val aiProviderName: String?,
    val aiModel: String?,
    val aiAnalysisAsOf: Instant?,
    val aiProposalId: UUID?,
    val accountProfileId: UUID?,
    val backtestRunId: UUID?,
    val paperOrderId: UUID?,
    val paperAccountId: UUID?,
    val status: TradeSetupStatus,
    val decisionNotes: String?,
    val decidedAt: Instant?,
    val exitPrice: BigDecimal?,
    val outcome: TradeSetupOutcome?,
    val closureNotes: String?,
    val closedAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        fun from(setup: TradeSetup) = TradeSetupResponse(
            id = setup.id,
            assetType = setup.assetType,
            symbol = setup.symbol,
            direction = setup.direction,
            entryPrice = setup.entryPrice,
            stopLossPrice = setup.stopLossPrice,
            targetPrice = setup.targetPrice,
            rationale = setup.rationale,
            source = setup.source,
            aiProviderName = setup.aiProviderName,
            aiModel = setup.aiModel,
            aiAnalysisAsOf = setup.aiAnalysisAsOf,
            aiProposalId = setup.aiProposalId,
            accountProfileId = setup.accountProfileId,
            backtestRunId = setup.backtestRunId,
            paperOrderId = setup.paperOrderId,
            paperAccountId = setup.paperAccountId,
            status = setup.status,
            decisionNotes = setup.decisionNotes,
            decidedAt = setup.decidedAt,
            exitPrice = setup.exitPrice,
            outcome = setup.outcome,
            closureNotes = setup.closureNotes,
            closedAt = setup.closedAt,
            createdAt = setup.createdAt,
            updatedAt = setup.updatedAt,
        )
    }
}
