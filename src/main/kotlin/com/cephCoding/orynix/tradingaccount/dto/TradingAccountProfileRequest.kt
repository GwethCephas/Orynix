package com.cephCoding.orynix.tradingaccount.dto

import com.cephCoding.orynix.tradingaccount.domain.TradingPlatform
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.math.BigDecimal

/**
 * Used for both create and update - a PUT replaces the full resource, so there is one shape, not
 * two. riskPercentPerTrade is the one field with service-applied default-on-null behavior (1%,
 * see TradingAccountService) rather than a Bean Validation @NotNull - this is a deliberate,
 * documented exception for exactly this field, not a general "nulls become defaults" policy.
 */
data class TradingAccountProfileRequest(
    @field:NotBlank
    @field:Size(max = 100)
    val name: String,

    @field:NotNull
    val platform: TradingPlatform,

    @field:Size(max = 255)
    val platformDetails: String?,

    @field:NotBlank
    @field:Pattern(regexp = "^[A-Z]{3}$", message = "accountCurrency must be a 3-letter ISO currency code")
    val accountCurrency: String,

    @field:NotNull
    @field:DecimalMin(value = "0", inclusive = true)
    val balance: BigDecimal,

    val riskPercentPerTrade: BigDecimal?,

    val maxDailyLossPercent: BigDecimal?,

    @field:Min(1)
    val maxConcurrentPositions: Int?,
)
