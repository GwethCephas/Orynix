package com.cephCoding.orynix.technicalanalysis.indicator

import kotlin.math.abs

data class DmiSeries(
    val plusDi: List<Double>,
    val minusDi: List<Double>,
    val adx: List<Double>,
)

/**
 * Wilder's Average Directional Index, +DI, and -DI. Reuses Atr.trueRange and Ema.wilderSmooth
 * directly rather than duplicating the true-range/Wilder-smoothing math Atr.kt already implements.
 */
object AdxCalculator {
    fun calculate(highs: List<Double>, lows: List<Double>, closes: List<Double>, period: Int = 14): DmiSeries {
        if (closes.size < 2) return DmiSeries(emptyList(), emptyList(), emptyList())

        val trueRange = Atr.trueRange(highs, lows, closes)
        // Aligned 1:1 with trueRange (both derived from the same (1 until closes.size) range):
        // plusDm[k]/minusDm[k] correspond to original candle index (k + 1).
        val plusDm = (1 until closes.size).map { i ->
            val upMove = highs[i] - highs[i - 1]
            val downMove = lows[i - 1] - lows[i]
            if (upMove > downMove && upMove > 0) upMove else 0.0
        }
        val minusDm = (1 until closes.size).map { i ->
            val upMove = highs[i] - highs[i - 1]
            val downMove = lows[i - 1] - lows[i]
            if (downMove > upMove && downMove > 0) downMove else 0.0
        }

        val smoothedTr = Ema.wilderSmooth(trueRange, period)
        val smoothedPlusDm = Ema.wilderSmooth(plusDm, period)
        val smoothedMinusDm = Ema.wilderSmooth(minusDm, period)
        if (smoothedTr.isEmpty()) return DmiSeries(emptyList(), emptyList(), emptyList())

        // A zero smoothed true range means no price movement at all over the window - there is no
        // directional information to report, so +DI/-DI are defined as 0 rather than dividing by
        // zero (never NaN/Infinity in a result, matching Rsi.kt's own zero-division precedent).
        val plusDi = smoothedTr.indices.map { j -> if (smoothedTr[j] == 0.0) 0.0 else 100.0 * smoothedPlusDm[j] / smoothedTr[j] }
        val minusDi = smoothedTr.indices.map { j -> if (smoothedTr[j] == 0.0) 0.0 else 100.0 * smoothedMinusDm[j] / smoothedTr[j] }
        val dx = plusDi.indices.map { j ->
            val sum = plusDi[j] + minusDi[j]
            if (sum == 0.0) 0.0 else 100.0 * abs(plusDi[j] - minusDi[j]) / sum
        }

        val adx = Ema.wilderSmooth(dx, period)
        return DmiSeries(plusDi, minusDi, adx)
    }
}
