package com.cephCoding.orynix.papertrading.model

import com.cephCoding.orynix.papertrading.domain.AccountStatus
import com.cephCoding.orynix.papertrading.domain.PaperTradingAccount
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
 * status persisted as plain VARCHAR, manually mapped (.name / AccountStatus.valueOf(...)) - no
 * @Enumerated, matching every other enum-like column in this schema. ACTIVE is set explicitly at
 * construction, not a DB default, matching TradingAccountProfileEntity.archived's precedent.
 */
@Entity
@Table(name = "paper_trading_accounts")
@EntityListeners(AuditingEntityListener::class)
class PaperTradingAccountEntity(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "user_id", nullable = false)
    val userId: UUID,

    @Column(nullable = false)
    var name: String,

    @Column(name = "base_currency", nullable = false)
    val baseCurrency: String,

    @Column(name = "initial_deposit", nullable = false)
    val initialDeposit: BigDecimal,

    @Column(nullable = false)
    var cash: BigDecimal,

    @Column(name = "commission_per_order", nullable = false)
    val commissionPerOrder: BigDecimal,

    @Column(name = "slippage_bps", nullable = false)
    val slippageBps: Int,

    @Column(nullable = false)
    var status: String = AccountStatus.ACTIVE.name,

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant? = null,

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant? = null,
) {
    fun toDomain() = PaperTradingAccount(
        id = id,
        userId = userId,
        name = name,
        baseCurrency = baseCurrency,
        initialDeposit = initialDeposit,
        cash = cash,
        commissionPerOrder = commissionPerOrder,
        slippageBps = slippageBps,
        status = AccountStatus.valueOf(status),
        createdAt = requireNotNull(createdAt),
        updatedAt = requireNotNull(updatedAt),
    )
}
