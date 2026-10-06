package com.cephCoding.orynix.signals.rule

import com.cephCoding.orynix.technicalanalysis.domain.TechnicalAnalysisResult

enum class CrossoverState { BULLISH_CROSSOVER, BEARISH_CROSSOVER, NONE }

object CrossoverRule {
    // Fires only on an actual transition between the two observations — EMA20 already being
    // above/below SMA50 in both is NOT a crossover, it's the steady state TrendRule already covers.
    fun evaluate(current: TechnicalAnalysisResult, previous: TechnicalAnalysisResult): CrossoverState {
        val wasAbove = previous.ema20 > previous.sma50
        val isAbove = current.ema20 > current.sma50
        return when {
            !wasAbove && isAbove -> CrossoverState.BULLISH_CROSSOVER
            wasAbove && !isAbove -> CrossoverState.BEARISH_CROSSOVER
            else -> CrossoverState.NONE
        }
    }
}
