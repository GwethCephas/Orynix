package com.cephCoding.orynix.tradesetup.service

import com.cephCoding.orynix.common.exception.InvalidTradeSetupException
import com.cephCoding.orynix.tradesetup.domain.TradeDirection
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.math.BigDecimal

/**
 * Pure function, no Spring context - matches BacktestService.computeWarmupFromDate /
 * MarketAnalysisService.validateAgainstMarket's precedent for testing a companion function
 * without constructing the full service.
 */
class TradeSetupPriceOrderingTest {

    private val entry = BigDecimal("100")

    @Test
    fun `LONG with stop below entry and target above entry is valid`() {
        TradeSetupService.validatePriceOrdering(TradeDirection.LONG, entry, BigDecimal("95"), BigDecimal("110"))
    }

    @Test
    fun `LONG with stop at or above entry is rejected`() {
        assertThrows(InvalidTradeSetupException::class.java) {
            TradeSetupService.validatePriceOrdering(TradeDirection.LONG, entry, BigDecimal("100"), null)
        }
        assertThrows(InvalidTradeSetupException::class.java) {
            TradeSetupService.validatePriceOrdering(TradeDirection.LONG, entry, BigDecimal("105"), null)
        }
    }

    @Test
    fun `LONG with target at or below entry is rejected`() {
        assertThrows(InvalidTradeSetupException::class.java) {
            TradeSetupService.validatePriceOrdering(TradeDirection.LONG, entry, null, BigDecimal("100"))
        }
        assertThrows(InvalidTradeSetupException::class.java) {
            TradeSetupService.validatePriceOrdering(TradeDirection.LONG, entry, null, BigDecimal("90"))
        }
    }

    @Test
    fun `SHORT with stop above entry and target below entry is valid`() {
        TradeSetupService.validatePriceOrdering(TradeDirection.SHORT, entry, BigDecimal("105"), BigDecimal("90"))
    }

    @Test
    fun `SHORT with stop at or below entry is rejected`() {
        assertThrows(InvalidTradeSetupException::class.java) {
            TradeSetupService.validatePriceOrdering(TradeDirection.SHORT, entry, BigDecimal("100"), null)
        }
        assertThrows(InvalidTradeSetupException::class.java) {
            TradeSetupService.validatePriceOrdering(TradeDirection.SHORT, entry, BigDecimal("95"), null)
        }
    }

    @Test
    fun `SHORT with target at or above entry is rejected`() {
        assertThrows(InvalidTradeSetupException::class.java) {
            TradeSetupService.validatePriceOrdering(TradeDirection.SHORT, entry, null, BigDecimal("100"))
        }
        assertThrows(InvalidTradeSetupException::class.java) {
            TradeSetupService.validatePriceOrdering(TradeDirection.SHORT, entry, null, BigDecimal("110"))
        }
    }

    @Test
    fun `null stop and target impose no ordering constraint for either direction`() {
        TradeSetupService.validatePriceOrdering(TradeDirection.LONG, entry, null, null)
        TradeSetupService.validatePriceOrdering(TradeDirection.SHORT, entry, null, null)
    }
}
