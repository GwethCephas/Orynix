package com.cephCoding.orynix.technicalanalysis.indicator

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MacdCalculatorTest {

    @Test
    fun `macd line is fast EMA minus slow EMA, signal is EMA of the macd line itself`() {
        // Linearly increasing closes: both EMAs trail the line by a constant offset, so the
        // macd line is a known constant, and the signal (EMA of a constant series) must equal
        // that same constant, with a zero histogram.
        val closes = listOf(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0)

        val result = MacdCalculator.calculate(closes, fastPeriod = 2, slowPeriod = 3, signalPeriod = 2)

        assertEquals(5, result.macdLine.size)
        result.macdLine.forEach { assertEquals(0.5, it, 1e-9) }
        assertEquals(4, result.signalLine.size)
        result.signalLine.forEach { assertEquals(0.5, it, 1e-9) }
        assertEquals(4, result.histogram.size)
        result.histogram.forEach { assertEquals(0.0, it, 1e-9) }
    }

    @Test
    fun `composition against independently-aligned fast and slow EMA for the real 12-26-9 periods`() {
        val closes = (0 until 60).map { 100.0 + it * 0.75 }

        val result = MacdCalculator.calculate(closes, fastPeriod = 12, slowPeriod = 26, signalPeriod = 9)

        val fastEma = Ema.calculate(closes, 12)
        val slowEma = Ema.calculate(closes, 26)
        val alignedFastEma = fastEma.subList(26 - 12, fastEma.size)
        val expectedMacdLine = alignedFastEma.indices.map { i -> alignedFastEma[i] - slowEma[i] }

        val expectedSignalLine = Ema.calculate(expectedMacdLine, 9)
        val alignedMacdLine = expectedMacdLine.subList(9 - 1, expectedMacdLine.size)
        val expectedHistogram = alignedMacdLine.indices.map { i -> alignedMacdLine[i] - expectedSignalLine[i] }

        assertEquals(expectedMacdLine.size, result.macdLine.size)
        expectedMacdLine.indices.forEach { i -> assertEquals(expectedMacdLine[i], result.macdLine[i], 1e-9) }

        assertEquals(expectedSignalLine.size, result.signalLine.size)
        expectedSignalLine.indices.forEach { i -> assertEquals(expectedSignalLine[i], result.signalLine[i], 1e-9) }

        assertEquals(expectedHistogram.size, result.histogram.size)
        expectedHistogram.indices.forEach { i -> assertEquals(expectedHistogram[i], result.histogram[i], 1e-9) }
    }

    @Test
    fun `insufficient input for the slow EMA returns an empty series`() {
        val closes = (1..10).map { it.toDouble() }

        val result = MacdCalculator.calculate(closes, fastPeriod = 12, slowPeriod = 26, signalPeriod = 9)

        assertEquals(emptyList<Double>(), result.macdLine)
        assertEquals(emptyList<Double>(), result.signalLine)
        assertEquals(emptyList<Double>(), result.histogram)
    }
}
