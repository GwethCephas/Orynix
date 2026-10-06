package com.cephCoding.orynix.technicalanalysis.indicator

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EmaTest {

    @Test
    fun `first value is SMA-seeded, later values smooth recursively`() {
        val values = listOf(1.0, 2.0, 3.0, 4.0, 5.0, 6.0)

        val result = Ema.calculate(values, period = 3)

        // seed = average(1,2,3) = 2.0; alpha = 2/(3+1) = 0.5
        assertEquals(4, result.size)
        assertEquals(2.0, result[0], 1e-9)
        assertEquals(3.0, result[1], 1e-9)
        assertEquals(4.0, result[2], 1e-9)
        assertEquals(5.0, result[3], 1e-9)
    }

    @Test
    fun `insufficient input returns an empty list`() {
        assertEquals(emptyList<Double>(), Ema.calculate(listOf(1.0, 2.0), period = 3))
    }

    @Test
    fun `wilder smoothing uses a different alpha than the standard EMA`() {
        val values = listOf(1.0, 2.0, 3.0, 4.0, 5.0, 6.0)

        val standard = Ema.calculate(values, period = 3)
        val wilder = Ema.wilderSmooth(values, period = 3)

        // Same SMA seed (2.0) for both, but they diverge immediately after since
        // standard alpha = 2/(period+1) = 0.5 while Wilder alpha = 1/period = 0.3333...
        assertEquals(standard[0], wilder[0], 1e-9)
        assertEquals(3.0, standard[1], 1e-9)
        assertEquals(2.0 + (1.0 / 3.0) * (4.0 - 2.0), wilder[1], 1e-9)
        assertEquals(2.6666666667, wilder[1], 1e-6)
    }
}
