package com.cephCoding.orynix.technicalanalysis.indicator

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.math.sqrt

class BollingerBandsCalculatorTest {

    @Test
    fun `middle band is the SMA and bands use population standard deviation`() {
        val closes = listOf(1.0, 2.0, 3.0, 4.0, 5.0, 6.0)

        val result = BollingerBandsCalculator.calculate(closes, period = 4, stdDevMultiplier = 2.0)

        // window [1,2,3,4]: mean 2.5, population variance = ((1.5)^2+(0.5)^2+(0.5)^2+(1.5)^2)/4 = 1.25
        val populationStdDev = sqrt(1.25)
        val sampleStdDev = sqrt(5.0 / 3.0) // what it would be with an (N-1) divisor instead

        assertEquals(3, result.middle.size)
        assertEquals(2.5, result.middle[0], 1e-9)
        assertEquals(2.5 + 2.0 * populationStdDev, result.upper[0], 1e-9)
        assertEquals(2.5 - 2.0 * populationStdDev, result.lower[0], 1e-9)

        // confirms the implementation is not accidentally using a sample (N-1) stddev
        assert(kotlin.math.abs(result.upper[0] - (2.5 + 2.0 * sampleStdDev)) > 1e-3)
    }

    @Test
    fun `insufficient input returns empty series`() {
        val result = BollingerBandsCalculator.calculate(listOf(1.0, 2.0), period = 4)

        assertEquals(emptyList<Double>(), result.middle)
        assertEquals(emptyList<Double>(), result.upper)
        assertEquals(emptyList<Double>(), result.lower)
    }
}
