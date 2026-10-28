package com.cephCoding.orynix.papertrading.domain

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * A dedicated, isolated virtual cash account for simulated trading - never the user's
 * manually-entered trading-account profile balance, never synced with or affecting any real
 * account. `cash` is ledger-derived and always exact; buying power/equity are NOT stored here -
 * they are computed fresh on every read from the latest acceptable price (see
 * PaperTradingAccounting), so this type only ever carries price-independent, always-live truth.
 */
data class PaperTradingAccount(
    val id: UUID,
    val userId: UUID,
    val name: String,
    val baseCurrency: String,
    val initialDeposit: BigDecimal,
    val cash: BigDecimal,
    val commissionPerOrder: BigDecimal,
    val slippageBps: Int,
    val status: AccountStatus,
    val createdAt: Instant,
    val updatedAt: Instant,
)
