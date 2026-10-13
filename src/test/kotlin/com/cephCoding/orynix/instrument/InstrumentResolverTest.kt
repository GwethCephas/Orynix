package com.cephCoding.orynix.instrument

import com.cephCoding.orynix.common.exception.InvalidSymbolException
import com.cephCoding.orynix.instrument.domain.AssetType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class InstrumentResolverTest {

    @Test
    fun `resolves a plain stock ticker unchanged`() {
        val instrument = InstrumentResolver.resolve("aapl")

        assertEquals(AssetType.STOCK, instrument.assetType)
        assertEquals("AAPL", instrument.displaySymbol)
        assertEquals("AAPL", instrument.providerSymbol)
        assertNull(instrument.baseCurrency)
        assertNull(instrument.quoteCurrency)
    }

    @Test
    fun `resolves a forex pair to its Massive provider ticker`() {
        val instrument = InstrumentResolver.resolve("eur/usd")

        assertEquals(AssetType.FOREX, instrument.assetType)
        assertEquals("EUR/USD", instrument.displaySymbol)
        assertEquals("C:EURUSD", instrument.providerSymbol)
        assertEquals("EUR", instrument.baseCurrency)
        assertEquals("USD", instrument.quoteCurrency)
    }

    @Test
    fun `trims whitespace before classifying`() {
        val instrument = InstrumentResolver.resolve("  gbp/usd  ")

        assertEquals(AssetType.FOREX, instrument.assetType)
        assertEquals("GBP/USD", instrument.displaySymbol)
    }

    @Test
    fun `rejects a malformed forex-shaped symbol`() {
        assertThrows(InvalidSymbolException::class.java) {
            InstrumentResolver.resolve("EURUSD/")
        }
    }

    @Test
    fun `rejects a symbol with too many currency segments`() {
        assertThrows(InvalidSymbolException::class.java) {
            InstrumentResolver.resolve("EUR/USD/JPY")
        }
    }

    @Test
    fun `rejects a symbol matching neither pattern`() {
        assertThrows(InvalidSymbolException::class.java) {
            InstrumentResolver.resolve("this-is-not-valid-123")
        }
    }

    @Test
    fun `a forex pair outside the curated list still resolves permissively`() {
        val instrument = InstrumentResolver.resolve("XXX/YYY")

        assertEquals(AssetType.FOREX, instrument.assetType)
        assertEquals("C:XXXYYY", instrument.providerSymbol)
    }

    @Test
    fun `re-resolving a displaySymbol is idempotent`() {
        // MarketIntelligenceService hands the same displaySymbol to TechnicalAnalysisService and
        // SignalAnalysisService, which independently re-resolve it via MarketDataService — all
        // three sections only agree on one symbol string because this round-trip holds.
        val first = InstrumentResolver.resolve("eur/usd")
        val second = InstrumentResolver.resolve(first.displaySymbol)

        assertEquals(first.displaySymbol, second.displaySymbol)
        assertEquals(first.providerSymbol, second.providerSymbol)
        assertEquals(first.assetType, second.assetType)
    }
}
