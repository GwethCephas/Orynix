package com.cephCoding.orynix.technicalanalysis.indicator

import kotlin.math.abs
import kotlin.math.max

object Atr {
    // One element shorter than the input series: the first true range needs a prior close.
    fun trueRange(highs: List<Double>, lows: List<Double>, closes: List<Double>): List<Double> {
        if (closes.size < 2) return emptyList()
        return (1 until closes.size).map { i ->
            val highLow = highs[i] - lows[i]
            val highPrevClose = abs(highs[i] - closes[i - 1])
            val lowPrevClose = abs(lows[i] - closes[i - 1])
            max(highLow, max(highPrevClose, lowPrevClose))
        }
    }

    fun calculate(highs: List<Double>, lows: List<Double>, closes: List<Double>, period: Int = 14): List<Double> =
        Ema.wilderSmooth(trueRange(highs, lows, closes), period)
}
