package com.cephCoding.orynix.tradingaccount.domain

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * A manually-entered, user-maintained trading account profile - never broker-verified. `balance`
 * is whatever the user last typed in; it is NOT synced with, derived from, or validated against
 * any real brokerage, and nothing in this codebase ever treats it as fact beyond "what the user
 * told us." See CLAUDE.md Section 19 for the full manual-vs-simulated-vs-broker-balance
 * distinction this type anchors. `maxDailyLossPercent`/`maxConcurrentPositions` are validated and
 * stored but NOT actively enforced in backtests today - also documented in Section 19, not hidden.
 */
data class TradingAccountProfile(
    val id: UUID,
    val userId: UUID,
    val name: String,
    val platform: TradingPlatform,
    val platformDetails: String?,
    val accountCurrency: String,
    val balance: BigDecimal,
    val riskPercentPerTrade: BigDecimal,
    val maxDailyLossPercent: BigDecimal?,
    val maxConcurrentPositions: Int?,
    val archived: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
)
