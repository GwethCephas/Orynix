package com.cephCoding.orynix.papertrading.domain

import java.math.BigDecimal

/**
 * Computed fresh on every read (summary GET, refresh action) from the latest acceptable price per
 * open position - never persisted as authoritative. See CLAUDE.md Section 22 for the full formula
 * derivation and reconciliation identity. `valuationWarnings` names any symbol whose price was
 * stale/unavailable at computation time, without blanking the rest of the account's numbers.
 */
data class AccountSummary(
    val cash: BigDecimal,
    val equity: BigDecimal,
    val buyingPower: BigDecimal,
    val marketValueLong: BigDecimal,
    val totalReservedCollateral: BigDecimal,
    val unrealizedPnlLong: BigDecimal,
    val unrealizedPnlShort: BigDecimal,
    val realizedPnlCumulative: BigDecimal,
    val lifetimeCommissionPaid: BigDecimal,
    val valuationWarnings: List<String>,
)
