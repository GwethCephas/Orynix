package com.cephCoding.orynix.backtesting.dto

import com.cephCoding.orynix.backtesting.domain.StrategyType
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.LocalDate

data class BacktestRequest(
    @field:NotBlank
    val symbol: String,

    @field:NotNull
    val fromDate: LocalDate,

    @field:NotNull
    val toDate: LocalDate,

    @field:NotNull
    val strategyType: StrategyType,

    val fastPeriod: Int?,
    val slowPeriod: Int?,
    val rsiPeriod: Int?,
    val oversoldThreshold: BigDecimal?,
    val overboughtThreshold: BigDecimal?,

    @field:NotNull
    @field:Positive
    val initialCapital: BigDecimal,

    @field:NotBlank
    @field:Size(min = 3, max = 3)
    val accountCurrency: String,

    @field:NotNull
    @field:Positive
    val quantity: BigDecimal,

    @field:NotNull
    @field:DecimalMin(value = "0", inclusive = true)
    val commissionPerOrder: BigDecimal,

    @field:NotNull
    @field:Min(0)
    val slippageBps: Int,
)
