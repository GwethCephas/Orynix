package com.cephCoding.orynix.papertrading.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class PaperTradingAccountingTest {

    // ---------- Long: open -> mark -> close, $1 commission per leg (CLAUDE.md Section 22 §0.3) ----------

    @Test
    fun `long open fill moves cash by exactly cost, equity unaffected beyond the commission drag`() {
        val open = PaperTradingAccounting.computeOpenLongFill(
            existingQty = BigDecimal.ZERO,
            existingAvg = BigDecimal.ZERO,
            addedQty = BigDecimal("10"),
            fillPrice = BigDecimal("150"),
            commission = BigDecimal("1"),
        )
        assertEquals(BigDecimal("-1501.0000"), open.cashDelta)
        assertEquals(BigDecimal("150.0000"), open.newAvgEntryPrice)
        assertEquals(BigDecimal("10.00000000"), open.newOpenQuantity)

        val cash = BigDecimal("10000").add(open.cashDelta)
        assertEquals(BigDecimal("8499.0000"), cash)

        val marketValue = PaperTradingAccounting.computeMarketValue(open.newOpenQuantity, BigDecimal("150"))
        val equity = PaperTradingAccounting.computeEquity(cash, marketValue, BigDecimal.ZERO)
        assertEquals(BigDecimal("9999.0000"), equity) // = 10000 - 1 (open commission), no price move yet
    }

    @Test
    fun `long reconciliation - mark up then fully close, no phantom gain or loss`() {
        val cashAfterOpen = BigDecimal("8499.0000")
        val markedEquity = PaperTradingAccounting.computeEquity(
            cashAfterOpen,
            PaperTradingAccounting.computeMarketValue(BigDecimal("10"), BigDecimal("160")),
            BigDecimal.ZERO,
        )
        assertEquals(BigDecimal("10099.0000"), markedEquity)
        assertEquals(
            BigDecimal("100.0000"),
            PaperTradingAccounting.computeUnrealizedPnlLong(BigDecimal("10"), BigDecimal("150"), BigDecimal("160")),
        )

        val close = PaperTradingAccounting.computeCloseLongFill(
            openQty = BigDecimal("10"),
            avgEntryPrice = BigDecimal("150"),
            closedQty = BigDecimal("10"),
            fillPrice = BigDecimal("160"),
            commission = BigDecimal("1"),
        )
        assertEquals(BigDecimal("1599.0000"), close.cashDelta)
        assertEquals(BigDecimal("99.0000"), close.realizedPnl)
        assertTrue(PaperTradingAccounting.isFullyClosed(BigDecimal("10"), BigDecimal("10")))

        val cashAfterClose = cashAfterOpen.add(close.cashDelta)
        assertEquals(BigDecimal("10098.0000"), cashAfterClose)
        // No open position left - equity must equal cash exactly, and must have dropped from the
        // pre-close marked equity by exactly the $1 closing commission, nothing else.
        assertEquals(markedEquity.subtract(BigDecimal("1.0000")), cashAfterClose)

        // Reconciliation identity: equity = D + realizedPnlCumulative - totalOpenCommissionEverPaid + unrealizedPnlTotal
        val d = BigDecimal("10000.0000")
        val reconciled = d.add(close.realizedPnl).subtract(BigDecimal("1.0000")).add(BigDecimal.ZERO)
        assertEquals(cashAfterClose, reconciled)
    }

    // ---------- Short: open -> mark -> close, collateralMultiplier=1.5, $1 commission per leg ----------

    @Test
    fun `short open fill credits proceeds to cash but reserves mark-to-market collateral, not the raw proceeds`() {
        val open = PaperTradingAccounting.computeOpenShortFill(
            existingQty = BigDecimal.ZERO,
            existingAvg = BigDecimal.ZERO,
            addedQty = BigDecimal("10"),
            fillPrice = BigDecimal("100"),
            commission = BigDecimal("1"),
        )
        assertEquals(BigDecimal("999.0000"), open.cashDelta)
        assertEquals(BigDecimal("100.0000"), open.newAvgEntryPrice)

        val cash = BigDecimal("10000").add(open.cashDelta)
        assertEquals(BigDecimal("10999.0000"), cash)

        val shortMarketValue = PaperTradingAccounting.computeMarketValue(open.newOpenQuantity, BigDecimal("100"))
        val equity = PaperTradingAccounting.computeEquity(cash, BigDecimal.ZERO, shortMarketValue)
        assertEquals(BigDecimal("9999.0000"), equity) // = 10000 - 1 (open commission), no price move yet

        val collateral = PaperTradingAccounting.computeReservedCollateral(open.newOpenQuantity, BigDecimal("100"), BigDecimal("1.5"))
        assertEquals(BigDecimal("1500.0000"), collateral)
        assertEquals(BigDecimal("9499.0000"), PaperTradingAccounting.computeBuyingPower(cash, collateral))
    }

    @Test
    fun `short reconciliation - mark favorably then fully close, no phantom gain or loss`() {
        val cashAfterOpen = BigDecimal("10999.0000")
        val markedShortValue = PaperTradingAccounting.computeMarketValue(BigDecimal("10"), BigDecimal("90"))
        val markedEquity = PaperTradingAccounting.computeEquity(cashAfterOpen, BigDecimal.ZERO, markedShortValue)
        assertEquals(BigDecimal("10099.0000"), markedEquity) // price dropped 10 on a 10-unit short = +100 gain
        assertEquals(
            BigDecimal("100.0000"),
            PaperTradingAccounting.computeUnrealizedPnlShort(BigDecimal("10"), BigDecimal("100"), BigDecimal("90")),
        )

        val close = PaperTradingAccounting.computeCloseShortFill(
            openQty = BigDecimal("10"),
            avgEntryPrice = BigDecimal("100"),
            closedQty = BigDecimal("10"),
            fillPrice = BigDecimal("90"),
            commission = BigDecimal("1"),
        )
        assertEquals(BigDecimal("-901.0000"), close.cashDelta)
        assertEquals(BigDecimal("99.0000"), close.realizedPnl)
        assertTrue(PaperTradingAccounting.isFullyClosed(BigDecimal("10"), BigDecimal("10")))

        val cashAfterClose = cashAfterOpen.add(close.cashDelta)
        assertEquals(BigDecimal("10098.0000"), cashAfterClose)
        assertEquals(markedEquity.subtract(BigDecimal("1.0000")), cashAfterClose)

        val d = BigDecimal("10000.0000")
        val reconciled = d.add(close.realizedPnl).subtract(BigDecimal("1.0000")).add(BigDecimal.ZERO)
        assertEquals(cashAfterClose, reconciled)
    }

    // ---------- The collateral multiplier is what actually bounds a short (regression) ----------

    @Test
    fun `without a multiplier greater than 1, opening a short costs only its commission - unbounded`() {
        val requiredAtNoMultiplier = PaperTradingAccounting.requiredBuyingPowerForOpenShort(
            qty = BigDecimal("100000"),
            fillPrice = BigDecimal("100"),
            commission = BigDecimal("1"),
            collateralMultiplier = BigDecimal("1.0"),
        )
        // A $10M notional short "costs" only $1 of buying power at multiplier 1.0 - this is the
        // exact bug the multiplier exists to close; asserting it here pins the regression.
        assertEquals(BigDecimal("1.0000"), requiredAtNoMultiplier)
    }

    @Test
    fun `collateral multiplier makes required buying power scale with notional, bounding short size`() {
        val required = PaperTradingAccounting.requiredBuyingPowerForOpenShort(
            qty = BigDecimal("10"),
            fillPrice = BigDecimal("100"),
            commission = BigDecimal("1"),
            collateralMultiplier = BigDecimal("1.5"),
        )
        // commission (1) + 0.5 * notional (0.5 * 1000 = 500)
        assertEquals(BigDecimal("501.0000"), required)
    }

    // ---------- Averaging and close-detection ----------

    @Test
    fun `adding to an existing position recomputes a correct weighted-average entry price`() {
        val newAvg = PaperTradingAccounting.weightedAverageEntryPrice(
            existingQty = BigDecimal("10"),
            existingAvg = BigDecimal("100"),
            addedQty = BigDecimal("10"),
            fillPrice = BigDecimal("120"),
        )
        assertEquals(BigDecimal("110.0000"), newAvg)
    }

    @Test
    fun `partial close leaves the position open with reduced quantity, full close is detected via compareTo`() {
        val partial = PaperTradingAccounting.computeCloseLongFill(
            openQty = BigDecimal("10"),
            avgEntryPrice = BigDecimal("100"),
            closedQty = BigDecimal("4"),
            fillPrice = BigDecimal("110"),
            commission = BigDecimal.ZERO,
        )
        assertEquals(BigDecimal("6.00000000"), partial.newOpenQuantity)
        assertFalse(PaperTradingAccounting.isFullyClosed(BigDecimal("10"), BigDecimal("4")))

        // Scale mismatch (10 vs 10.00000000) must not defeat full-close detection.
        assertTrue(PaperTradingAccounting.isFullyClosed(BigDecimal("10"), BigDecimal("10.00000000")))
    }
}
