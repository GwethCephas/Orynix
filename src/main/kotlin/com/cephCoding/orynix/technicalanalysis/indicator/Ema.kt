package com.cephCoding.orynix.technicalanalysis.indicator

object Ema {
    fun calculate(values: List<Double>, period: Int): List<Double> =
        smooth(values, period, alpha = 2.0 / (period + 1))

    // Wilder smoothing is an EMA variant with alpha = 1/period, used by RSI and ATR.
    fun wilderSmooth(values: List<Double>, period: Int): List<Double> =
        smooth(values, period, alpha = 1.0 / period)

    // SMA-seeded: the first output is the simple average of the first `period` values;
    // every value after that is smoothed recursively. This seeding convention is a deliberate
    // choice (not standardized across implementations) made explicit here per Orynix's
    // determinism requirement for technical indicators.
    private fun smooth(values: List<Double>, period: Int, alpha: Double): List<Double> {
        if (values.size < period) return emptyList()
        val seed = values.subList(0, period).average()
        val result = mutableListOf(seed)
        for (i in period until values.size) {
            val previous = result.last()
            result.add(previous + alpha * (values[i] - previous))
        }
        return result
    }
}
