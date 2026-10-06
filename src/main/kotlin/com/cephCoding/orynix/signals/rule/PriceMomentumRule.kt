package com.cephCoding.orynix.signals.rule

import com.cephCoding.orynix.market.domain.Candle

enum class PriceMomentumState { POSITIVE, NEGATIVE, FLAT }

data class PriceMomentumAssessment(val state: PriceMomentumState, val changePercent: Double)

object PriceMomentumRule {
    fun evaluate(candles: List<Candle>, lookback: Int, flatPercent: Double): PriceMomentumAssessment {
        val latestClose = candles.last().close.toDouble()
        val previousClose = candles[candles.size - 1 - lookback].close.toDouble()
        val changePercent = (latestClose - previousClose) / previousClose * 100.0
        val state = when {
            changePercent > flatPercent -> PriceMomentumState.POSITIVE
            changePercent < -flatPercent -> PriceMomentumState.NEGATIVE
            else -> PriceMomentumState.FLAT
        }
        return PriceMomentumAssessment(state, changePercent)
    }
}
