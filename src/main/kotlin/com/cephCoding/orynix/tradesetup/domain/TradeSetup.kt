package com.cephCoding.orynix.tradesetup.domain

import com.cephCoding.orynix.instrument.domain.AssetType
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * A persistent, reviewable trade idea - sourced from the AI Market Analysis Agent or entered
 * manually. Never represents verified broker activity: ACCEPTED means the user accepted the idea
 * for consideration, not that a trade was executed; CLOSED means the user reported it closed, not
 * that a broker confirmed it.
 *
 * `aiProposalId`, not `source == AI_ANALYSIS`, is the only trustworthy "this really came from the
 * AI agent" signal (CLAUDE.md Section 21's Integration A). A setup created via the older
 * `POST /trade-setups` path can still declare `source = AI_ANALYSIS` with client-resubmitted
 * `aiProviderName`/`aiModel`/`aiAnalysisAsOf` that are never verified against anything - that path
 * is kept for backward compatibility, not removed. Only a setup created via
 * `POST /trade-setups/from-ai-proposal` (which copies every AI-provenance field from a durable,
 * server-persisted `AiTradeSetupProposalEntity` looked up by id) has a non-null `aiProposalId`.
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
)
