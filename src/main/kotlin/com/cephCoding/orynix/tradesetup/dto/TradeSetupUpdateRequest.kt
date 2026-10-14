package com.cephCoding.orynix.tradesetup.dto

import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import java.math.BigDecimal
import java.util.UUID

/**
 * direction/symbol/source/AI-provenance are immutable for the row's entire life and are
 * deliberately absent here - only allowed while status is DRAFT (enforced in TradeSetupService).
 */
data class TradeSetupUpdateRequest(
    @field:NotNull
    @field:Positive
    val entryPrice: BigDecimal,

    @field:Positive
    val stopLossPrice: BigDecimal?,

    @field:Positive
    val targetPrice: BigDecimal?,

    val rationale: String?,

    val accountProfileId: UUID?,
)
