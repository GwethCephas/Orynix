package com.cephCoding.orynix.signals.rule

import com.cephCoding.orynix.technicalanalysis.domain.TechnicalAnalysisResult

enum class RsiState { OVERBOUGHT, OVERSOLD, NEUTRAL }

object RsiRule {
    fun evaluate(current: TechnicalAnalysisResult, overbought: Double, oversold: Double): RsiState {
        val rsi = current.rsi14.toDouble()
        return when {
            rsi >= overbought -> RsiState.OVERBOUGHT
            rsi <= oversold -> RsiState.OVERSOLD
            else -> RsiState.NEUTRAL
        }
    }
}
