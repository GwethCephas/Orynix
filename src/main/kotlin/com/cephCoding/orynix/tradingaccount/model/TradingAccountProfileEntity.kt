package com.cephCoding.orynix.tradingaccount.model

import com.cephCoding.orynix.tradingaccount.domain.TradingAccountProfile
import com.cephCoding.orynix.tradingaccount.domain.TradingPlatform
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
 * platform persisted as plain VARCHAR, manually mapped (.name / TradingPlatform.valueOf(...)) - no
 * @Enumerated, matching MarketDataCandle.timeframe / NewsSentimentEntity.sentiment's precedent.
 */
@Entity
@Table(name = "trading_account_profiles")
@EntityListeners(AuditingEntityListener::class)
class TradingAccountProfileEntity(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "user_id", nullable = false)
    val userId: UUID,

    @Column(nullable = false)
    var name: String,

    @Column(nullable = false)
    var platform: String,

    @Column(name = "platform_details")
    var platformDetails: String?,

    @Column(name = "account_currency", nullable = false)
    var accountCurrency: String,

    @Column(nullable = false)
    var balance: BigDecimal,

    @Column(name = "risk_percent_per_trade", nullable = false)
    var riskPercentPerTrade: BigDecimal,

    @Column(name = "max_daily_loss_percent")
    var maxDailyLossPercent: BigDecimal?,

    @Column(name = "max_concurrent_positions")
    var maxConcurrentPositions: Int?,

    @Column(nullable = false)
    var archived: Boolean = false,

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant? = null,

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant? = null,
) {
    fun toDomain() = TradingAccountProfile(
        id = id,
        userId = userId,
        name = name,
        platform = TradingPlatform.valueOf(platform),
        platformDetails = platformDetails,
        accountCurrency = accountCurrency,
        balance = balance,
        riskPercentPerTrade = riskPercentPerTrade,
        maxDailyLossPercent = maxDailyLossPercent,
        maxConcurrentPositions = maxConcurrentPositions,
        archived = archived,
        createdAt = requireNotNull(createdAt),
        updatedAt = requireNotNull(updatedAt),
    )
}
