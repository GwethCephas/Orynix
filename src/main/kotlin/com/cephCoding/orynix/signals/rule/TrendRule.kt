package com.cephCoding.orynix.signals.rule

import com.cephCoding.orynix.technicalanalysis.domain.TechnicalAnalysisResult

enum class TrendState { BULLISH, BEARISH, NEUTRAL }

object TrendRule {
    fun evaluate(current: TechnicalAnalysisResult): TrendState = when {
        current.ema20 > current.sma20 && current.sma20 > current.sma50 -> TrendState.BULLISH
        current.ema20 < current.sma20 && current.sma20 < current.sma50 -> TrendState.BEARISH
        else -> TrendState.NEUTRAL
    }
}
