package com.cephCoding.orynix.technicalanalysis.indicator

data class DonchianSeries(
    val upper: List<Double>,
    val lower: List<Double>,
)

object DonchianCalculator {
    // Deliberately different from every other rolling-window indicator in this package (Sma,
    // BollingerBandsCalculator): upper[k]/lower[k] are computed from the `period` candles
    // PRECEDING original index (k + period) - i.e. highs/lows.subList(k, k + period) - excluding
    // the candle AT that index itself. A breakout channel must never use the current candle's own
    // high/low to set the threshold that same candle is tested against.
    fun calculate(highs: List<Double>, lows: List<Double>, period: Int): DonchianSeries {
        if (highs.size <= period) return DonchianSeries(emptyList(), emptyList())

        val upper = (0 until highs.size - period).map { k -> highs.subList(k, k + period).max() }
        val lower = (0 until lows.size - period).map { k -> lows.subList(k, k + period).min() }
        return DonchianSeries(upper, lower)
    }
}
