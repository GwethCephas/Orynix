package com.cephCoding.orynix.tradesetup.domain

import com.cephCoding.orynix.instrument.domain.AssetType
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * A persistent, reviewable trade idea - sourced from the AI Market Analysis Agent (client-
 * resubmitted proposal fields, see TradeSetupSource) or entered manually. Never represents
 * verified broker activity: ACCEPTED means the user accepted the idea for consideration, not that
 * a trade was executed; CLOSED means the user reported it closed, not that a broker confirmed it.
 */
data class TradeSetup(
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
    val accountProfileId: UUID?,
    val status: TradeSetupStatus,
    val decisionNotes: String?,
    val decidedAt: Instant?,
    val exitPrice: BigDecimal?,
    val outcome: TradeSetupOutcome?,
    val closureNotes: String?,
    val closedAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
)
