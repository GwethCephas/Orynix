package com.cephCoding.orynix.technicalanalysis.indicator

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class VolumeIndicatorsTest {

    @Test
    fun `volume SMA matches the generic SMA calculation`() {
        val volumes = listOf(100.0, 200.0, 300.0, 400.0)

        assertEquals(Sma.calculate(volumes, 2), VolumeIndicators.sma(volumes, 2))
    }

    @Test
    fun `change percentage matches the standard formula`() {
        val volumes = listOf(100.0, 200.0, 50.0, 0.0, 10.0)

        val result = VolumeIndicators.changePercent(volumes)

        assertEquals(4, result.size)
        assertEquals(100.0, result[0]!!, 1e-9)
        assertEquals(-75.0, result[1]!!, 1e-9)
        assertEquals(-100.0, result[2]!!, 1e-9)
    }

    @Test
    fun `zero previous volume is undefined, not fabricated`() {
        val volumes = listOf(100.0, 200.0, 50.0, 0.0, 10.0)

        val result = VolumeIndicators.changePercent(volumes)

        assertEquals(null, result[3])
    }

    @Test
    fun `fewer than two volumes yields no change series`() {
        assertEquals(emptyList<Double?>(), VolumeIndicators.changePercent(listOf(100.0)))
    }
}
