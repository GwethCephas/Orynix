package com.cephCoding.orynix.technicalanalysis.indicator

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RsiTest {

    @Test
    fun `matches hand-computed RSI for a mixed gain-loss series`() {
        // changes: +0.5, -1.0, +1.5, +1.0 -> gains [0.5,0,1.5,1.0], losses [0,1.0,0,0]
        val closes = listOf(44.0, 44.5, 43.5, 45.0, 46.0)

        val result = Rsi.calculate(closes, period = 3)

        val avgGain0 = (0.5 + 0.0 + 1.5) / 3.0
        val avgLoss0 = (0.0 + 1.0 + 0.0) / 3.0
        val expectedRsi0 = 100.0 - 100.0 / (1.0 + avgGain0 / avgLoss0)

        val avgGain1 = avgGain0 + (1.0 / 3.0) * (1.0 - avgGain0)
        val avgLoss1 = avgLoss0 + (1.0 / 3.0) * (0.0 - avgLoss0)
        val expectedRsi1 = 100.0 - 100.0 / (1.0 + avgGain1 / avgLoss1)

        assertEquals(2, result.size)
        assertEquals(expectedRsi0, result[0], 1e-6)
        assertEquals(expectedRsi1, result[1], 1e-6)
    }

    @Test
    fun `all-gains period approaches 100 without dividing by zero`() {
        val closes = listOf(10.0, 11.0, 12.0, 13.0)

        val result = Rsi.calculate(closes, period = 3)

        assertEquals(1, result.size)
        assertEquals(100.0, result[0], 1e-9)
    }

    @Test
    fun `all-losses period is zero, not NaN`() {
        val closes = listOf(13.0, 12.0, 11.0, 10.0)

        val result = Rsi.calculate(closes, period = 3)

        assertEquals(1, result.size)
        assertEquals(0.0, result[0], 1e-9)
    }

    @Test
    fun `insufficient input returns an empty list`() {
        val closes = listOf(10.0, 11.0, 12.0)

        assertEquals(emptyList<Double>(), Rsi.calculate(closes, period = 3))
    }
}
