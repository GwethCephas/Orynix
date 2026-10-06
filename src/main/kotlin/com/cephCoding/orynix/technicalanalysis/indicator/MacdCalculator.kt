package com.cephCoding.orynix.technicalanalysis.indicator

data class MacdSeries(
    val macdLine: List<Double>,
    val signalLine: List<Double>,
    val histogram: List<Double>,
)

object MacdCalculator {
    fun calculate(
        closes: List<Double>,
        fastPeriod: Int = 12,
        slowPeriod: Int = 26,
        signalPeriod: Int = 9,
    ): MacdSeries {
        val fastEma = Ema.calculate(closes, fastPeriod)
        val slowEma = Ema.calculate(closes, slowPeriod)
        if (fastEma.isEmpty() || slowEma.isEmpty()) {
            return MacdSeries(emptyList(), emptyList(), emptyList())
        }

        // fastEma starts earlier than slowEma (smaller period); align both to slowEma's start
        // index before subtracting, since the two series are offset by (slowPeriod - fastPeriod).
        val startOffset = slowPeriod - fastPeriod
        val alignedFastEma = fastEma.subList(startOffset, fastEma.size)
        val macdLine = alignedFastEma.indices.map { i -> alignedFastEma[i] - slowEma[i] }

        val signalLine = Ema.calculate(macdLine, signalPeriod)
        if (signalLine.isEmpty()) {
            return MacdSeries(macdLine, emptyList(), emptyList())
        }

        val signalOffset = signalPeriod - 1
        val alignedMacdLine = macdLine.subList(signalOffset, macdLine.size)
        val histogram = alignedMacdLine.indices.map { i -> alignedMacdLine[i] - signalLine[i] }

        return MacdSeries(macdLine, signalLine, histogram)
    }
}
