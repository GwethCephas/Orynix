package com.cephCoding.orynix.tradesetup.dto

import com.cephCoding.orynix.tradesetup.domain.TradeSetupDecision
import jakarta.validation.constraints.NotNull

data class TradeSetupDecisionRequest(
    @field:NotNull
    val decision: TradeSetupDecision,

    val notes: String?,
)
