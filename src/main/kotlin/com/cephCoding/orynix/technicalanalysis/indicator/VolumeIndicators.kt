package com.cephCoding.orynix.technicalanalysis.indicator

object VolumeIndicators {
    fun sma(volumes: List<Double>, period: Int = 20): List<Double> = Sma.calculate(volumes, period)

    // Null where the previous period's volume is zero — the percentage change is mathematically
    // undefined there, not zero or infinite, so it must not be fabricated.
    fun changePercent(volumes: List<Double>): List<Double?> {
        if (volumes.size < 2) return emptyList()
        return (1 until volumes.size).map { i ->
            val previous = volumes[i - 1]
            if (previous == 0.0) null else ((volumes[i] - previous) / previous) * 100.0
        }
    }
}
