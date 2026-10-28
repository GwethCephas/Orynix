package com.cephCoding.orynix.papertrading.dto

import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.math.BigDecimal

// quantity null means "close the full remaining open quantity" - the thin convenience wrapper
// described in CLAUDE.md Section 22; a supplied value closes only that much (partial close).
data class ClosePositionRequest(
    @field:DecimalMin(value = "0", inclusive = false)
    val quantity: BigDecimal?,

    @field:NotBlank
    @field:Size(max = 100)
    val idempotencyKey: String,
)
