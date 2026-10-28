package com.cephCoding.orynix.papertrading.model

import com.cephCoding.orynix.instrument.domain.AssetType
import com.cephCoding.orynix.papertrading.domain.OrderSide
import com.cephCoding.orynix.papertrading.domain.OrderStatus
import com.cephCoding.orynix.papertrading.domain.PaperOrder
import com.cephCoding.orynix.papertrading.domain.PositionEffect
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EntityListeners
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Immutable once written - no updatedAt, matching backtest_trades/news_sentiments' "computed
 * once, never updated" precedent. status is always FILLED this milestone (see OrderStatus); a
 * rejection is a thrown exception with no row ever written, so there is nothing to update to.
 */
@Entity
@Table(name = "paper_orders")
@EntityListeners(AuditingEntityListener::class)
class PaperOrderEntity(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "account_id", nullable = false)
    val accountId: UUID,

    @Column(name = "position_id")
    val positionId: UUID?,

    @Column(nullable = false)
    val symbol: String,

    @Column(name = "asset_type", nullable = false)
    val assetType: String,

    @Column(nullable = false)
    val side: String,

    @Column(name = "position_effect", nullable = false)
    val positionEffect: String,

    @Column(name = "requested_quantity", nullable = false)
    val requestedQuantity: BigDecimal,

    @Column(name = "filled_quantity", nullable = false)
    val filledQuantity: BigDecimal,

    @Column(name = "fill_price", nullable = false)
    val fillPrice: BigDecimal,

    @Column(nullable = false)
    val commission: BigDecimal,

    @Column(nullable = false)
    val status: String = OrderStatus.FILLED.name,

    @Column(name = "source_candle_timestamp", nullable = false)
    val sourceCandleTimestamp: Instant,

    @Column(name = "execution_timestamp", nullable = false)
    val executionTimestamp: Instant,

    @Column(name = "execution_assumptions", nullable = false)
    val executionAssumptions: String,

    @Column(name = "idempotency_key", nullable = false)
    val idempotencyKey: String,

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant? = null,
) {
    fun toDomain() = PaperOrder(
        id = id,
        positionId = positionId,
        symbol = symbol,
        assetType = AssetType.valueOf(assetType),
        side = OrderSide.valueOf(side),
        positionEffect = PositionEffect.valueOf(positionEffect),
        requestedQuantity = requestedQuantity,
        filledQuantity = filledQuantity,
        fillPrice = fillPrice,
        commission = commission,
        status = OrderStatus.valueOf(status),
        sourceCandleTimestamp = sourceCandleTimestamp,
        executionTimestamp = executionTimestamp,
        executionAssumptions = executionAssumptions,
        idempotencyKey = idempotencyKey,
        createdAt = requireNotNull(createdAt),
    )
}
