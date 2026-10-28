package com.cephCoding.orynix.papertrading.dto

import com.cephCoding.orynix.papertrading.domain.OrderSide
import com.cephCoding.orynix.papertrading.domain.PositionEffect
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.math.BigDecimal

/**
 * Market order only - no order type field, since this milestone implements nothing else (see
 * CLAUDE.md Section 22). side + positionEffect are both required and independent - never infer
 * one from the other. stopLossPrice/takeProfitPrice only apply when positionEffect=OPEN (rejected
 * outright, not silently ignored, when supplied on a CLOSE - see
 * PaperTradingOrderService.validateOrderShape).
 */
data class SubmitPaperOrderRequest(
    @field:NotBlank
    val symbol: String,

    @field:NotNull
    val side: OrderSide,

    @field:NotNull
    val positionEffect: PositionEffect,

    @field:NotNull
    @field:DecimalMin(value = "0", inclusive = false)
    val quantity: BigDecimal,

    @field:NotBlank
    @field:Size(max = 100)
    val idempotencyKey: String,

    val stopLossPrice: BigDecimal?,

    val takeProfitPrice: BigDecimal?,
)
