package com.cephCoding.orynix.signals.rule

import com.cephCoding.orynix.technicalanalysis.domain.TechnicalAnalysisResult

enum class MacdState { BULLISH_CROSSOVER, BEARISH_CROSSOVER, BULLISH_MOMENTUM, BEARISH_MOMENTUM, NEUTRAL_MOMENTUM }

object MacdRule {
    // A crossover this period takes priority over a momentum classification — one combined MACD
    // signal, not two. signum() (not ==) decides momentum, since BigDecimal equality is
    // scale-sensitive and a bare "== ZERO" would misclassify a near-zero histogram.
    fun evaluate(current: TechnicalAnalysisResult, previous: TechnicalAnalysisResult): MacdState {
        val wasAbove = previous.macd.macd > previous.macd.signal
        val isAbove = current.macd.macd > current.macd.signal
        return when {
            !wasAbove && isAbove -> MacdState.BULLISH_CROSSOVER
            wasAbove && !isAbove -> MacdState.BEARISH_CROSSOVER
            else -> when (current.macd.histogram.signum()) {
                1 -> MacdState.BULLISH_MOMENTUM
                -1 -> MacdState.BEARISH_MOMENTUM
                else -> MacdState.NEUTRAL_MOMENTUM
            }
        }
    }
}
