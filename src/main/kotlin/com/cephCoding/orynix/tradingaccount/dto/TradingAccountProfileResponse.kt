package com.cephCoding.orynix.tradingaccount.dto

import com.cephCoding.orynix.tradingaccount.domain.TradingAccountProfile
import com.cephCoding.orynix.tradingaccount.domain.TradingPlatform
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * brokerVerified is always false - there is no broker integration anywhere in this codebase, so
 * `balance` is never anything but what the user typed in (CLAUDE.md Section 19). The two
 * `*EnforcedInBacktests` fields are always false today and exist specifically so the frontend can
 * render an honest "not yet enforced" disclosure next to those inputs rather than implying a
 * stored-but-silently-inert setting works - see Section 19 for why real enforcement was deferred
 * rather than faked.
 */
data class TradingAccountProfileResponse(
    val id: UUID,
    val name: String,
    val platform: TradingPlatform,
    val platformDetails: String?,
    val accountCurrency: String,
    val balance: BigDecimal,
    val riskPercentPerTrade: BigDecimal,
    val maxDailyLossPercent: BigDecimal?,
    val maxConcurrentPositions: Int?,
    val maxDailyLossPercentEnforcedInBacktests: Boolean,
    val maxConcurrentPositionsEnforcedInBacktests: Boolean,
    val brokerVerified: Boolean,
    val archived: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        fun from(profile: TradingAccountProfile) = TradingAccountProfileResponse(
            id = profile.id,
            name = profile.name,
            platform = profile.platform,
            platformDetails = profile.platformDetails,
            accountCurrency = profile.accountCurrency,
            balance = profile.balance,
            riskPercentPerTrade = profile.riskPercentPerTrade,
            maxDailyLossPercent = profile.maxDailyLossPercent,
            maxConcurrentPositions = profile.maxConcurrentPositions,
            maxDailyLossPercentEnforcedInBacktests = false,
            maxConcurrentPositionsEnforcedInBacktests = false,
            brokerVerified = false,
            archived = profile.archived,
            createdAt = profile.createdAt,
            updatedAt = profile.updatedAt,
        )
    }
}
