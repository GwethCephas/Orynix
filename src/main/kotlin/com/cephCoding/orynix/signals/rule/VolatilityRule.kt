package com.cephCoding.orynix.signals.rule

import com.cephCoding.orynix.technicalanalysis.domain.TechnicalAnalysisResult
import java.math.BigDecimal

enum class VolatilityState { LOW, NORMAL, HIGH }

data class VolatilityAssessment(val state: VolatilityState, val atrPercent: Double)

object VolatilityRule {
    // Relative measure (ATR as a percent of price), never a raw absolute ATR threshold — ATR's
    // magnitude depends heavily on the asset's price, so an absolute threshold isn't portable
    // across symbols.
    fun evaluate(current: TechnicalAnalysisResult, closePrice: BigDecimal, lowPercent: Double, highPercent: Double): VolatilityAssessment {
        val atrPercent = current.atr14.toDouble() / closePrice.toDouble() * 100.0
        val state = when {
            atrPercent < lowPercent -> VolatilityState.LOW
            atrPercent > highPercent -> VolatilityState.HIGH
            else -> VolatilityState.NORMAL
        }
        return VolatilityAssessment(state, atrPercent)
    }
}
