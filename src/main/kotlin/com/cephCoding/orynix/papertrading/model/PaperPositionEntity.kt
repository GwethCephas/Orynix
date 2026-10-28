package com.cephCoding.orynix.papertrading.model

import com.cephCoding.orynix.instrument.domain.AssetType
import com.cephCoding.orynix.papertrading.domain.PaperPosition
import com.cephCoding.orynix.papertrading.domain.PositionDirection
import com.cephCoding.orynix.papertrading.domain.PositionStatus
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
 * assetType/direction/status persisted as plain VARCHAR, manually mapped - no @Enumerated,
 * matching every other enum-like column in this schema. The DB partial unique index
 * (account_id, symbol) WHERE status = 'OPEN' is the backstop for "one open position per
 * instrument" - the service layer is the actual gate (CLAUDE.md Section 22).
 */
@Entity
@Table(name = "paper_positions")
@EntityListeners(AuditingEntityListener::class)
class PaperPositionEntity(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "account_id", nullable = false)
    val accountId: UUID,

    @Column(nullable = false)
    val symbol: String,

    @Column(name = "asset_type", nullable = false)
    val assetType: String,

    @Column(nullable = false)
    val direction: String,

    @Column(nullable = false)
    var status: String,

    @Column(name = "open_quantity", nullable = false)
    var openQuantity: BigDecimal,

    @Column(name = "avg_entry_price", nullable = false)
    var avgEntryPrice: BigDecimal,

    @Column(name = "total_open_commission_paid", nullable = false)
    var totalOpenCommissionPaid: BigDecimal,

    @Column(name = "realized_pnl", nullable = false)
    var realizedPnl: BigDecimal,

    @Column(name = "stop_loss_price")
    var stopLossPrice: BigDecimal?,

    @Column(name = "take_profit_price")
    var takeProfitPrice: BigDecimal?,

    @Column(name = "opened_at", nullable = false)
    val openedAt: Instant,

    @Column(name = "closed_at")
    var closedAt: Instant? = null,

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant? = null,

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant? = null,
) {
    fun toDomain() = PaperPosition(
        id = id,
        symbol = symbol,
        assetType = AssetType.valueOf(assetType),
        direction = PositionDirection.valueOf(direction),
        status = PositionStatus.valueOf(status),
        openQuantity = openQuantity,
        avgEntryPrice = avgEntryPrice,
        totalOpenCommissionPaid = totalOpenCommissionPaid,
        realizedPnl = realizedPnl,
        stopLossPrice = stopLossPrice,
        takeProfitPrice = takeProfitPrice,
        openedAt = openedAt,
        closedAt = closedAt,
        createdAt = requireNotNull(createdAt),
        updatedAt = requireNotNull(updatedAt),
    )
}
