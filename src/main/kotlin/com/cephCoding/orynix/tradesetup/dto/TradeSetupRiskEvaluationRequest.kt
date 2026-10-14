package com.cephCoding.orynix.tradesetup.dto

import java.math.BigDecimal
import java.util.UUID

/**
 * Mirrors RiskCalculationRequest's override shape - all fields optional, each overriding the
 * setup's own stored accountProfileId / falling back to it when accountProfileId is omitted here.
 * entryPrice/stopLossPrice/takeProfitPrice are NOT here - they always come from the setup's own
 * stored, persisted values, never from this request.
 */
data class TradeSetupRiskEvaluationRequest(
    val accountProfileId: UUID? = null,
    val equity: BigDecimal? = null,
    val accountCurrency: String? = null,
    val riskPercentPerTrade: BigDecimal? = null,
)
