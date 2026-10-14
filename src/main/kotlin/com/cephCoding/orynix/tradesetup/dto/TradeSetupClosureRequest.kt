package com.cephCoding.orynix.tradesetup.dto

import com.cephCoding.orynix.tradesetup.domain.TradeSetupOutcome
import jakarta.validation.constraints.Positive
import java.math.BigDecimal

/** All fields optional - a user may report a closure without yet knowing/wanting to record every detail. */
data class TradeSetupClosureRequest(
    @field:Positive
    val exitPrice: BigDecimal?,

    val outcome: TradeSetupOutcome?,

    val closureNotes: String?,
)
