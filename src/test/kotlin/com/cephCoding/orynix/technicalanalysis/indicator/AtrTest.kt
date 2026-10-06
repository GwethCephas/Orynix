package com.cephCoding.orynix.technicalanalysis.indicator

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AtrTest {

    @Test
    fun `true range is the max of the three component terms, each dominating once`() {
        // i=1: high-low (10) dominates over |high-prevClose| (5) and |low-prevClose| (5)
        // i=2: |high-prevClose| (15) dominates over high-low (2) and |low-prevClose| (13)
        // i=3: |low-prevClose| (16) dominates over high-low (10) and |high-prevClose| (6)
        val highs = listOf(10.0, 15.0, 20.0, 12.0)
        val lows = listOf(10.0, 5.0, 18.0, 2.0)
        val closes = listOf(10.0, 5.0, 18.0, 2.0)

        val result = Atr.trueRange(highs, lows, closes)

        assertEquals(3, result.size)
        assertEquals(closes.size - 1, result.size)
        assertEquals(10.0, result[0], 1e-9)
        assertEquals(15.0, result[1], 1e-9)
        assertEquals(16.0, result[2], 1e-9)
    }

    @Test
    fun `first ATR value is the plain average of the first period true ranges`() {
        val highs = listOf(10.0, 15.0, 20.0, 12.0)
        val lows = listOf(10.0, 5.0, 18.0, 2.0)
        val closes = listOf(10.0, 5.0, 18.0, 2.0)

        val result = Atr.calculate(highs, lows, closes, period = 3)

        assertEquals(1, result.size)
        assertEquals((10.0 + 15.0 + 16.0) / 3.0, result[0], 1e-9)
    }

    @Test
    fun `fewer than two candles yields no true range`() {
        assertEquals(emptyList<Double>(), Atr.trueRange(listOf(10.0), listOf(9.0), listOf(9.5)))
    }
}
