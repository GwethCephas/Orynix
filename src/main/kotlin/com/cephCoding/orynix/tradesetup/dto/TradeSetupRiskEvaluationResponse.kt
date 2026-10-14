package com.cephCoding.orynix.tradesetup.dto

import com.cephCoding.orynix.riskcalculator.dto.RiskCalculationResponse
import com.cephCoding.orynix.tradesetup.domain.TradeSetupRiskEvaluation

/**
 * Reuses RiskCalculationResponse directly as the nested payload rather than inventing a
 * duplicate shape - all 16 of its fields are genuinely wanted here. limitations at this level
 * explains why evaluation wasn't attempted at all (SHORT direction, missing stop-loss); when
 * sizing is non-null, its own nested limitations explains calculation-specific constraints (e.g.
 * UNSUPPORTED) - two different concerns, not redundant.
 */
data class TradeSetupRiskEvaluationResponse(
    val sizing: RiskCalculationResponse?,
    val limitations: List<String>,
) {
    companion object {
        fun from(evaluation: TradeSetupRiskEvaluation) = TradeSetupRiskEvaluationResponse(
            sizing = evaluation.sizing?.let { RiskCalculationResponse.from(it) },
            limitations = evaluation.limitations,
        )
    }
}
