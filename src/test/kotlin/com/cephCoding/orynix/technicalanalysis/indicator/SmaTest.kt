package com.cephCoding.orynix.technicalanalysis.indicator

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SmaTest {

    @Test
    fun `calculates a sliding window average`() {
        val values = listOf(1.0, 2.0, 3.0, 4.0, 5.0)

        val result = Sma.calculate(values, period = 3)

        assertEquals(3, result.size)
        assertEquals(2.0, result[0], 1e-9)
        assertEquals(3.0, result[1], 1e-9)
        assertEquals(4.0, result[2], 1e-9)
    }

    @Test
    fun `exact-length window produces a single average`() {
        val values = listOf(1.0, 2.0, 3.0)

        val result = Sma.calculate(values, period = 3)

        assertEquals(listOf(2.0), result)
    }

    @Test
    fun `insufficient input returns an empty list`() {
        val values = listOf(1.0, 2.0)

        val result = Sma.calculate(values, period = 3)

        assertEquals(emptyList<Double>(), result)
    }
}
