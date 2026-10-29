package com.cephCoding.orynix.tradesetup.dto

import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.util.UUID

/**
 * Initiates a real (simulated) paper-trading order from an ACCEPTED trade setup (CLAUDE.md
 * Section 21's Integration C) - "Practice in Paper Trading". `idempotencyKey` is caller-supplied
 * and passed straight through to PaperTradingOrderService.submitOrder unchanged, exactly like the
 * existing Paper Trading page already mints one per submission intent and reuses it on retry; this
 * endpoint adds no idempotency behavior of its own beyond what that service already provides.
 */
data class TradeSetupPaperOrderRequest(
    @field:NotNull
    val paperAccountId: UUID,

    @field:NotNull
    @field:DecimalMin(value = "0", inclusive = false)
    val quantity: BigDecimal,

    @field:NotBlank
    @field:Size(max = 100)
    val idempotencyKey: String,
)
