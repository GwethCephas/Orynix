package com.cephCoding.orynix.papertrading.dto

import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.math.BigDecimal

// commissionPerOrder/slippageBps default to zero when omitted - explicit simulated fee/slippage
// assumptions per account, mirroring BacktestRequest's identically-named fields.
data class CreatePaperTradingAccountRequest(
    @field:NotBlank
    @field:Size(max = 100)
    val name: String,

    @field:NotBlank
    @field:Pattern(regexp = "^[A-Z]{3}$", message = "baseCurrency must be a 3-letter ISO currency code")
    val baseCurrency: String,

    @field:NotNull
    @field:DecimalMin(value = "0", inclusive = false)
    val initialDeposit: BigDecimal,

    @field:DecimalMin(value = "0", inclusive = true)
    val commissionPerOrder: BigDecimal? = null,

    @field:Min(0)
    val slippageBps: Int? = null,
)
