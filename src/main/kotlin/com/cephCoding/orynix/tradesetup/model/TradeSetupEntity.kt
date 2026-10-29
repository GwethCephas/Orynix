package com.cephCoding.orynix.tradesetup.model

import com.cephCoding.orynix.instrument.domain.AssetType
import com.cephCoding.orynix.tradesetup.domain.TradeDirection
import com.cephCoding.orynix.tradesetup.domain.TradeSetup
import com.cephCoding.orynix.tradesetup.domain.TradeSetupOutcome
import com.cephCoding.orynix.tradesetup.domain.TradeSetupSource
import com.cephCoding.orynix.tradesetup.domain.TradeSetupStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EntityListeners
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * assetType/direction/source/status/outcome persisted as plain VARCHAR, manually mapped
 * (.name / Enum.valueOf(...)) - no @Enumerated, matching every other enum-like column in this
 * schema. No DB-side default for status - DRAFT is set at construction here in Kotlin, matching
 * TradingAccountProfileEntity.archived's "service-layer default, not a DB default" precedent.
 */
@Entity
@Table(name = "trade_setups")
@EntityListeners(AuditingEntityListener::class)
class TradeSetupEntity(
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
    var entryPrice: BigDecimal,

    @Column(name = "stop_loss_price")
    var stopLossPrice: BigDecimal?,

    @Column(name = "target_price")
    var targetPrice: BigDecimal?,

    @Column
    var rationale: String?,

    @Column(nullable = false)
    val source: String,

    @Column(name = "ai_provider_name")
    val aiProviderName: String?,

    @Column(name = "ai_model")
    val aiModel: String?,

    @Column(name = "ai_analysis_as_of")
    val aiAnalysisAsOf: Instant?,

    @Column(name = "ai_proposal_id")
    val aiProposalId: UUID? = null,

    @Column(name = "account_profile_id")
    var accountProfileId: UUID?,

    @Column(name = "backtest_run_id")
    var backtestRunId: UUID? = null,

    @Column(name = "paper_order_id")
    var paperOrderId: UUID? = null,

    @Column(name = "paper_account_id")
    var paperAccountId: UUID? = null,

    @Column(nullable = false)
    var status: String = TradeSetupStatus.DRAFT.name,

    @Column(name = "decision_notes")
    var decisionNotes: String? = null,

    @Column(name = "decided_at")
    var decidedAt: Instant? = null,

    @Column(name = "exit_price")
    var exitPrice: BigDecimal? = null,

    @Column
    var outcome: String? = null,

    @Column(name = "closure_notes")
    var closureNotes: String? = null,

    @Column(name = "closed_at")
    var closedAt: Instant? = null,

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant? = null,

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant? = null,
) {
    fun toDomain() = TradeSetup(
        id = id,
        assetType = AssetType.valueOf(assetType),
        symbol = symbol,
        direction = TradeDirection.valueOf(direction),
        entryPrice = entryPrice,
        stopLossPrice = stopLossPrice,
        targetPrice = targetPrice,
        rationale = rationale,
        source = TradeSetupSource.valueOf(source),
        aiProviderName = aiProviderName,
        aiModel = aiModel,
        aiAnalysisAsOf = aiAnalysisAsOf,
        aiProposalId = aiProposalId,
        accountProfileId = accountProfileId,
        backtestRunId = backtestRunId,
        paperOrderId = paperOrderId,
        paperAccountId = paperAccountId,
        status = TradeSetupStatus.valueOf(status),
        decisionNotes = decisionNotes,
        decidedAt = decidedAt,
        exitPrice = exitPrice,
        outcome = outcome?.let(TradeSetupOutcome::valueOf),
        closureNotes = closureNotes,
        closedAt = closedAt,
        createdAt = requireNotNull(createdAt),
        updatedAt = requireNotNull(updatedAt),
    )
}
