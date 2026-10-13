package com.cephCoding.orynix.instrument

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ForexPairCatalogTest {

    @Test
    fun `returns the curated description for a known pair`() {
        val instrument = InstrumentResolver.resolve("EUR/USD")

        val description = ForexPairCatalog.describe(instrument)

        assertTrue(description.contains("Euro"))
    }

    @Test
    fun `falls back to a generic description for an uncurated pair`() {
        val instrument = InstrumentResolver.resolve("XXX/YYY")

        val description = ForexPairCatalog.describe(instrument)

        assertEquals("XXX/YYY currency pair.", description)
    }
}
