package com.cephCoding.orynix.agents.dto

import jakarta.validation.constraints.NotBlank
import java.math.BigDecimal
import java.util.UUID

/**
 * equity/accountCurrency/riskPercentPerTrade follow the identical precedence contract
 * RiskCalculationRequest already established (CLAUDE.md Section 19/20): an explicit value wins
 * over the selected profile's. All four sizing-related fields are optional - a pure market read
 * with no trade-setup sizing is a valid, common request (an empty response trade-setup list is
 * valid too; this module never forces sizing to be attempted).
 */
data class MarketAnalysisRequest(
    @field:NotBlank
    val symbol: String,

    val accountProfileId: UUID? = null,
    val equity: BigDecimal? = null,
    val accountCurrency: String? = null,
    val riskPercentPerTrade: BigDecimal? = null,
)
