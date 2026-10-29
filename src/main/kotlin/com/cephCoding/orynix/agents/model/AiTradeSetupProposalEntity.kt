package com.cephCoding.orynix.agents.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Durable record of one AI-proposed trade setup that survived MarketAnalysisService's semantic
 * sanity check - the server-verifiable counterpart to the fully-ephemeral MarketAnalysisResponse
 * (CLAUDE.md Section 20/21's Integration A). assetType/direction persisted as plain VARCHAR,
 * matching every other enum-like column in this schema - no @Enumerated. analyzedAt is set
 * explicitly at construction (never @CreatedDate/AuditingEntityListener), matching
 * NewsSentimentEntity/BacktestRunEntity's "computed once, never updated" precedent (Section 11/18):
 * a proposal is a point-in-time snapshot of what the model said, never edited after creation.
 */
@Entity
@Table(name = "ai_trade_setup_proposals")
class AiTradeSetupProposalEntity(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "user_id", nullable = false)
    val userId: UUID,

    @Column(name = "asset_type", nullable = false)
    val assetType: String,

    @Column(nullable = false)
    val symbol: String,

    @Column(nullable = false)
    val direction: String,

    @Column(name = "entry_price", nullable = false)
    val entryPrice: BigDecimal,

    @Column(name = "stop_loss_price", nullable = false)
    val stopLossPrice: BigDecimal,

    @Column(name = "take_profit_price")
    val takeProfitPrice: BigDecimal?,

    @Column(nullable = false)
    val rationale: String,

    @Column(name = "provider_name", nullable = false)
    val providerName: String,

    @Column(nullable = false)
    val model: String,

    @Column(name = "analysis_as_of", nullable = false)
    val analysisAsOf: Instant,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
)
