package com.cephCoding.orynix.technicalanalysis.indicator

import kotlin.math.sqrt

data class BollingerSeries(
    val upper: List<Double>,
    val middle: List<Double>,
    val lower: List<Double>,
)

object BollingerBandsCalculator {
    fun calculate(closes: List<Double>, period: Int = 20, stdDevMultiplier: Double = 2.0): BollingerSeries {
        if (closes.size < period) {
            return BollingerSeries(emptyList(), emptyList(), emptyList())
        }

        val middle = Sma.calculate(closes, period)
        // Population standard deviation (divide by `period`, not `period - 1`) — the band width
        // is defined over the fixed look-back window itself, not a sample estimate of a larger population.
        val stdDevs = (period..closes.size).map { end ->
            val window = closes.subList(end - period, end)
            val mean = window.average()
            val variance = window.sumOf { (it - mean) * (it - mean) } / period
            sqrt(variance)
        }

        val upper = middle.indices.map { i -> middle[i] + stdDevMultiplier * stdDevs[i] }
        val lower = middle.indices.map { i -> middle[i] - stdDevMultiplier * stdDevs[i] }
        return BollingerSeries(upper, middle, lower)
    }
}
