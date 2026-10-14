package com.cephCoding.orynix.riskcalculator.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import java.math.BigDecimal
import java.util.UUID

/**
 * equity/accountCurrency/riskPercentPerTrade each follow the identical precedence rule
 * BacktestRequest.accountProfileId established (CLAUDE.md Section 19): when accountProfileId is
 * supplied, the profile fills in whichever of these three fields the request leaves null; an
 * explicit value in this same request always wins over the profile's. At least one source
 * (explicit or profile) is required for each - enforced in RiskCalculatorService, since Bean
 * Validation cannot express "required unless accountProfileId is supplied".
 */
data class RiskCalculationRequest(
    @field:NotBlank
    val symbol: String,

    val accountProfileId: UUID? = null,

    val equity: BigDecimal?,

    val accountCurrency: String?,

    val riskPercentPerTrade: BigDecimal?,

    @field:NotNull
    @field:Positive
    val entryPrice: BigDecimal,

    @field:NotNull
    @field:Positive
    val stopLossPrice: BigDecimal,

    // Optional - only used to compute riskRewardRatio/estimatedGainAtTakeProfit when present.
    val takeProfitPrice: BigDecimal?,
)
