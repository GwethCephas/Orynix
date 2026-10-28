package com.cephCoding.orynix.papertrading.domain

import java.math.BigDecimal
import java.time.Instant

/**
 * Ephemeral, computed-fresh-on-every-read valuation for one open position - never persisted as
 * authoritative (matches TradeSetupRiskEvaluation's "always ephemeral, never cached" idiom).
 * `reservedCollateral` is null for a long (the concept only applies to shorts - see CLAUDE.md
 * Section 22's collateral-multiplier mechanism).
 */
data class PositionValuation(
    val markPrice: BigDecimal,
    val priceStatus: PriceStatus,
    val priceAsOf: Instant,
    val marketValue: BigDecimal,
    val unrealizedPnl: BigDecimal,
    val reservedCollateral: BigDecimal?,
)
