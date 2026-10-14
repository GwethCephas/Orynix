package com.cephCoding.orynix.backtesting.engine

import com.cephCoding.orynix.backtesting.domain.ExitReason
import com.cephCoding.orynix.backtesting.strategy.BacktestStrategy
import com.cephCoding.orynix.market.domain.Candle
import com.cephCoding.orynix.market.domain.CandleTimeframe
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant

class BacktestEngineTest {

    private class ScriptedStrategy(
        private val script: Map<Int, StrategyAction> = emptyMap(),
        override val warmupPeriod: Int = 0,
    ) : BacktestStrategy {
        override fun signalAt(index: Int, candles: List<Candle>): StrategyAction =
            script[index] ?: StrategyAction.HOLD
    }

    private fun candle(day: Int, open: Double, close: Double): Candle = Candle(
        symbol = "TEST",
        timeframe = CandleTimeframe.DAILY,
        timestamp = Instant.parse("2026-01-01T00:00:00Z").plusSeconds(day * 86_400L),
        open = BigDecimal.valueOf(open),
        high = BigDecimal.valueOf(maxOf(open, close)),
        low = BigDecimal.valueOf(minOf(open, close)),
        close = BigDecimal.valueOf(close),
        volume = BigDecimal.valueOf(1_000.0),
    )

    // Wick-capable overload, additive only - the no-wick candle(day, open, close) above is left
    // completely untouched so none of the pre-existing tests need editing.
    private fun candle(day: Int, open: Double, high: Double, low: Double, close: Double): Candle = Candle(
        symbol = "TEST",
        timeframe = CandleTimeframe.DAILY,
        timestamp = Instant.parse("2026-01-01T00:00:00Z").plusSeconds(day * 86_400L),
        open = BigDecimal.valueOf(open),
        high = BigDecimal.valueOf(high),
        low = BigDecimal.valueOf(low),
        close = BigDecimal.valueOf(close),
        volume = BigDecimal.valueOf(1_000.0),
    )

    @Test
    fun `fill executes at the next bar open, not the signal bar close, and force-closes at the final bar`() {
        val candles = listOf(
            candle(0, open = 100.0, close = 110.0),
            candle(1, open = 120.0, close = 130.0),
            candle(2, open = 140.0, close = 150.0),
        )
        val strategy = ScriptedStrategy(mapOf(0 to StrategyAction.ENTER_LONG))

        val result = BacktestEngine.run(
            candles = candles,
            firstTradableIndex = 0,
            strategy = strategy,
            initialCapital = BigDecimal("1000"),
            quantity = BigDecimal("1"),
            commissionPerOrder = BigDecimal.ZERO,
            slippageBps = 0,
        )

        assertEquals(BigDecimal("880.0000"), result.equityCurve[1].cash)
        assertEquals(1, result.trades.size)
        val trade = result.trades[0]
        assertEquals(BigDecimal("120.0000"), trade.entryPrice)
        assertEquals(BigDecimal("150.0000"), trade.exitPrice)
        assertEquals(ExitReason.END_OF_PERIOD, trade.exitReason)
        assertEquals(1, result.entrySignalsGenerated)
        assertEquals(0, result.exitSignalsGenerated)
        assertEquals(0, result.entriesSkippedInsufficientCash)
        assertEquals(3, result.candlesEvaluated)
    }

    @Test
    fun `an exit signal closes the position at the next bar open with reason SIGNAL`() {
        val candles = listOf(
            candle(0, open = 100.0, close = 100.0),
            candle(1, open = 100.0, close = 100.0),
            candle(2, open = 110.0, close = 110.0),
        )
        val strategy = ScriptedStrategy(mapOf(0 to StrategyAction.ENTER_LONG, 1 to StrategyAction.EXIT_LONG))

        val result = BacktestEngine.run(
            candles = candles,
            firstTradableIndex = 0,
            strategy = strategy,
            initialCapital = BigDecimal("1000"),
            quantity = BigDecimal("1"),
            commissionPerOrder = BigDecimal.ZERO,
            slippageBps = 0,
        )

        assertEquals(1, result.trades.size)
        val trade = result.trades[0]
        assertEquals(ExitReason.SIGNAL, trade.exitReason)
        assertEquals(BigDecimal("100.0000"), trade.entryPrice)
        assertEquals(BigDecimal("110.0000"), trade.exitPrice)
        assertEquals(1, result.winningTrades)
        assertEquals(0, result.losingTrades)
        assertNull(result.profitFactor)
        assertEquals(1, result.entrySignalsGenerated)
        assertEquals(1, result.exitSignalsGenerated)
        assertEquals(0, result.entriesSkippedInsufficientCash)
    }

    @Test
    fun `slippage raises the buy fill price and lowers the sell fill price`() {
        val candles = listOf(
            candle(0, open = 100.0, close = 100.0),
            candle(1, open = 100.0, close = 100.0),
            candle(2, open = 200.0, close = 200.0),
        )
        val strategy = ScriptedStrategy(mapOf(0 to StrategyAction.ENTER_LONG, 1 to StrategyAction.EXIT_LONG))

        val result = BacktestEngine.run(
            candles = candles,
            firstTradableIndex = 0,
            strategy = strategy,
            initialCapital = BigDecimal("1000"),
            quantity = BigDecimal("1"),
            commissionPerOrder = BigDecimal.ZERO,
            slippageBps = 100,
        )

        val trade = result.trades[0]
        assertEquals(BigDecimal("101.0000"), trade.entryPrice)
        assertEquals(BigDecimal("198.0000"), trade.exitPrice)
    }

    @Test
    fun `commission is charged once per fill, twice per round trip`() {
        val candles = listOf(
            candle(0, open = 100.0, close = 100.0),
            candle(1, open = 100.0, close = 100.0),
            candle(2, open = 110.0, close = 110.0),
        )
        val strategy = ScriptedStrategy(mapOf(0 to StrategyAction.ENTER_LONG, 1 to StrategyAction.EXIT_LONG))

        val result = BacktestEngine.run(
            candles = candles,
            firstTradableIndex = 0,
            strategy = strategy,
            initialCapital = BigDecimal("1000"),
            quantity = BigDecimal("1"),
            commissionPerOrder = BigDecimal("5"),
            slippageBps = 0,
        )

        val trade = result.trades[0]
        assertEquals(BigDecimal("10.0000"), trade.commission)
        assertEquals(BigDecimal("10.0000"), trade.grossPnl)
        assertEquals(BigDecimal("0.0000"), trade.netPnl)
    }

    @Test
    fun `a signal on the final candle is never evaluated and produces no trade`() {
        val candles = listOf(
            candle(0, open = 100.0, close = 100.0),
            candle(1, open = 100.0, close = 100.0),
            candle(2, open = 100.0, close = 100.0),
        )
        val strategy = ScriptedStrategy(mapOf(2 to StrategyAction.ENTER_LONG))

        val result = BacktestEngine.run(
            candles = candles,
            firstTradableIndex = 0,
            strategy = strategy,
            initialCapital = BigDecimal("1000"),
            quantity = BigDecimal("1"),
            commissionPerOrder = BigDecimal.ZERO,
            slippageBps = 0,
        )

        assertTrue(result.trades.isEmpty())
        assertEquals(BigDecimal("1000.0000"), result.finalEquity)
    }

    @Test
    fun `an entry skipped for insufficient cash produces no trade and no exception`() {
        val candles = listOf(
            candle(0, open = 100.0, close = 100.0),
            candle(1, open = 100.0, close = 100.0),
        )
        val strategy = ScriptedStrategy(mapOf(0 to StrategyAction.ENTER_LONG))

        val result = BacktestEngine.run(
            candles = candles,
            firstTradableIndex = 0,
            strategy = strategy,
            initialCapital = BigDecimal("50"),
            quantity = BigDecimal("1"),
            commissionPerOrder = BigDecimal.ZERO,
            slippageBps = 0,
        )

        assertTrue(result.trades.isEmpty())
        assertEquals(BigDecimal("50.0000"), result.finalEquity)
        assertEquals(1, result.entrySignalsGenerated)
        assertEquals(1, result.entriesSkippedInsufficientCash)
        assertEquals(0, result.exitSignalsGenerated)
    }

    @Test
    fun `zero trades leaves winRate and profitFactor undefined, not zero`() {
        val candles = listOf(
            candle(0, open = 100.0, close = 100.0),
            candle(1, open = 100.0, close = 100.0),
        )
        val strategy = ScriptedStrategy()

        val result = BacktestEngine.run(
            candles = candles,
            firstTradableIndex = 0,
            strategy = strategy,
            initialCapital = BigDecimal("1000"),
            quantity = BigDecimal("1"),
            commissionPerOrder = BigDecimal.ZERO,
            slippageBps = 0,
        )

        assertNull(result.winRate)
        assertNull(result.profitFactor)
        assertEquals(0, result.totalTrades)
        assertEquals(0, result.entrySignalsGenerated)
        assertEquals(0, result.entriesSkippedInsufficientCash)
    }

    @Test
    fun `a dip then recovery produces a known max drawdown and a breakeven forced close`() {
        val candles = listOf(
            candle(0, open = 100.0, close = 100.0),
            candle(1, open = 100.0, close = 80.0),
            candle(2, open = 100.0, close = 120.0),
            candle(3, open = 100.0, close = 100.0),
        )
        val strategy = ScriptedStrategy(mapOf(0 to StrategyAction.ENTER_LONG))

        val result = BacktestEngine.run(
            candles = candles,
            firstTradableIndex = 0,
            strategy = strategy,
            initialCapital = BigDecimal("1000"),
            quantity = BigDecimal("1"),
            commissionPerOrder = BigDecimal.ZERO,
            slippageBps = 0,
        )

        assertEquals(BigDecimal("2.0000"), result.maxDrawdownPercent)
        assertEquals(1, result.totalTrades)
        assertEquals(0, result.winningTrades)
        assertEquals(0, result.losingTrades)
        assertEquals(1, result.breakevenTrades)
        assertEquals(BigDecimal("0.0000"), result.winRate)
        assertNull(result.profitFactor)
        assertEquals(ExitReason.END_OF_PERIOD, result.trades[0].exitReason)
    }

    @Test
    fun `identical inputs produce identical outputs`() {
        val candles = listOf(
            candle(0, open = 100.0, close = 100.0),
            candle(1, open = 100.0, close = 100.0),
            candle(2, open = 110.0, close = 110.0),
        )
        fun runOnce() = BacktestEngine.run(
            candles = candles,
            firstTradableIndex = 0,
            strategy = ScriptedStrategy(mapOf(0 to StrategyAction.ENTER_LONG, 1 to StrategyAction.EXIT_LONG)),
            initialCapital = BigDecimal("1000"),
            quantity = BigDecimal("1"),
            commissionPerOrder = BigDecimal("2"),
            slippageBps = 10,
        )

        assertEquals(runOnce(), runOnce())
    }

    // --- ATR risk management -----------------------------------------------------------------
    // All ATR values below are hand-verified against Atr.calculate's actual Wilder-smoothing
    // algorithm (SMA-seeded, alpha = 1/period), not assumed - see per-test comments for the trace.

    @Test
    fun `initial stop uses ATR at the signal bar, not the fill bar`() {
        // period 1: wilderSmooth seeds from a single value and alpha=1, so atrAt(i) is exactly
        // candle i's own true range, with no smoothing lag - the simplest alignment to hand-verify.
        // Signal fires at index 1 (small true range there); the fill bar (index 2) has a huge
        // true range. A correct engine uses the signal bar's ATR (2) for the stop, not the fill
        // bar's (100) - which would produce a wildly different (and here, negative) stop.
        val candles = listOf(
            candle(0, open = 100.0, high = 100.0, low = 100.0, close = 100.0),
            candle(1, open = 100.0, high = 101.0, low = 99.0, close = 100.0), // TR = 2
            candle(2, open = 100.0, high = 150.0, low = 50.0, close = 120.0), // TR = 100, entry fill bar
            candle(3, open = 120.0, high = 125.0, low = 115.0, close = 122.0), // final bar
        )
        val strategy = ScriptedStrategy(mapOf(1 to StrategyAction.ENTER_LONG))

        val result = BacktestEngine.run(
            candles = candles,
            firstTradableIndex = 1,
            strategy = strategy,
            initialCapital = BigDecimal("1000"),
            quantity = BigDecimal("1"),
            commissionPerOrder = BigDecimal.ZERO,
            slippageBps = 0,
            riskManagementConfig = RiskManagementConfig(
                atrPeriod = 1,
                initialStopMultiplier = BigDecimal("1"),
                trailingEnabled = false,
                trailingStopMultiplier = BigDecimal.ZERO,
            ),
        )

        assertEquals(1, result.trades.size)
        val trade = result.trades[0]
        assertEquals(BigDecimal("100.0000"), trade.entryPrice)
        assertEquals(BigDecimal("98.0000"), trade.initialStopPrice)
        assertEquals(ExitReason.END_OF_PERIOD, trade.exitReason)
    }

    @Test
    fun `the entry bar itself is exempt from the stop check`() {
        // Days 0-2 flat (TR=4 each, period 2) give atrAt(2)=4, so the stop set at entry is
        // fillPrice(100) - 4 = 96. The entry bar's own low (50) is far below that - if the entry
        // bar were not exempt, this would wrongly trigger ATR_STOP on the entry bar itself.
        val candles = listOf(
            candle(0, open = 100.0, high = 102.0, low = 98.0, close = 100.0),
            candle(1, open = 100.0, high = 102.0, low = 98.0, close = 100.0),
            candle(2, open = 100.0, high = 102.0, low = 98.0, close = 100.0),
            candle(3, open = 100.0, high = 102.0, low = 50.0, close = 100.0), // entry bar, low=50 ignored
            candle(4, open = 100.0, high = 102.0, low = 98.0, close = 105.0), // final bar, low 98 > stop 96
        )
        val strategy = ScriptedStrategy(mapOf(2 to StrategyAction.ENTER_LONG))

        val result = BacktestEngine.run(
            candles = candles,
            firstTradableIndex = 2,
            strategy = strategy,
            initialCapital = BigDecimal("1000"),
            quantity = BigDecimal("1"),
            commissionPerOrder = BigDecimal.ZERO,
            slippageBps = 0,
            riskManagementConfig = RiskManagementConfig(
                atrPeriod = 2,
                initialStopMultiplier = BigDecimal("1"),
                trailingEnabled = false,
                trailingStopMultiplier = BigDecimal.ZERO,
            ),
        )

        assertEquals(1, result.trades.size)
        val trade = result.trades[0]
        assertEquals(BigDecimal("96.0000"), trade.initialStopPrice)
        assertEquals(ExitReason.END_OF_PERIOD, trade.exitReason)
        assertEquals(BigDecimal("105.0000"), trade.exitPrice)
    }

    @Test
    fun `a gap below the stop fills at the open, not the stop price`() {
        // Same entry setup as above (stop = 96). The next bar gaps open below the stop (open=90)
        // with an even lower low (85) - the fill must use the open (90), not the stop price (96).
        val candles = listOf(
            candle(0, open = 100.0, high = 102.0, low = 98.0, close = 100.0),
            candle(1, open = 100.0, high = 102.0, low = 98.0, close = 100.0),
            candle(2, open = 100.0, high = 102.0, low = 98.0, close = 100.0),
            candle(3, open = 100.0, high = 102.0, low = 98.0, close = 100.0), // entry fill bar
            candle(4, open = 90.0, high = 92.0, low = 85.0, close = 91.0), // gap below stop
            candle(5, open = 90.0, high = 92.0, low = 88.0, close = 90.0), // trailing bar
        )
        val strategy = ScriptedStrategy(mapOf(2 to StrategyAction.ENTER_LONG))

        val result = BacktestEngine.run(
            candles = candles,
            firstTradableIndex = 2,
            strategy = strategy,
            initialCapital = BigDecimal("1000"),
            quantity = BigDecimal("1"),
            commissionPerOrder = BigDecimal.ZERO,
            slippageBps = 0,
            riskManagementConfig = RiskManagementConfig(
                atrPeriod = 2,
                initialStopMultiplier = BigDecimal("1"),
                trailingEnabled = false,
                trailingStopMultiplier = BigDecimal.ZERO,
            ),
        )

        assertEquals(1, result.trades.size)
        val trade = result.trades[0]
        assertEquals(ExitReason.ATR_STOP, trade.exitReason)
        assertEquals(BigDecimal("90.0000"), trade.exitPrice)
        assertEquals(BigDecimal("96.0000"), trade.initialStopPrice)
    }

    @Test
    fun `a low piercing the stop without a gap fills at the stop price`() {
        val candles = listOf(
            candle(0, open = 100.0, high = 102.0, low = 98.0, close = 100.0),
            candle(1, open = 100.0, high = 102.0, low = 98.0, close = 100.0),
            candle(2, open = 100.0, high = 102.0, low = 98.0, close = 100.0),
            candle(3, open = 100.0, high = 102.0, low = 98.0, close = 100.0), // entry fill bar
            candle(4, open = 98.0, high = 99.0, low = 90.0, close = 95.0), // open above stop, low pierces it
            candle(5, open = 95.0, high = 97.0, low = 93.0, close = 95.0),
        )
        val strategy = ScriptedStrategy(mapOf(2 to StrategyAction.ENTER_LONG))

        val result = BacktestEngine.run(
            candles = candles,
            firstTradableIndex = 2,
            strategy = strategy,
            initialCapital = BigDecimal("1000"),
            quantity = BigDecimal("1"),
            commissionPerOrder = BigDecimal.ZERO,
            slippageBps = 0,
            riskManagementConfig = RiskManagementConfig(
                atrPeriod = 2,
                initialStopMultiplier = BigDecimal("1"),
                trailingEnabled = false,
                trailingStopMultiplier = BigDecimal.ZERO,
            ),
        )

        assertEquals(1, result.trades.size)
        val trade = result.trades[0]
        assertEquals(ExitReason.ATR_STOP, trade.exitReason)
        assertEquals(BigDecimal("96.0000"), trade.exitPrice)
    }

    @Test
    fun `price staying above the stop exits normally via a strategy signal`() {
        val candles = listOf(
            candle(0, open = 100.0, high = 102.0, low = 98.0, close = 100.0),
            candle(1, open = 100.0, high = 102.0, low = 98.0, close = 100.0),
            candle(2, open = 100.0, high = 102.0, low = 98.0, close = 100.0),
            candle(3, open = 100.0, high = 102.0, low = 98.0, close = 100.0), // entry fill bar
            candle(4, open = 105.0, high = 107.0, low = 103.0, close = 105.0), // signal fires here
            candle(5, open = 110.0, high = 112.0, low = 108.0, close = 110.0), // signal fills here
        )
        val strategy = ScriptedStrategy(mapOf(2 to StrategyAction.ENTER_LONG, 4 to StrategyAction.EXIT_LONG))

        val result = BacktestEngine.run(
            candles = candles,
            firstTradableIndex = 2,
            strategy = strategy,
            initialCapital = BigDecimal("1000"),
            quantity = BigDecimal("1"),
            commissionPerOrder = BigDecimal.ZERO,
            slippageBps = 0,
            riskManagementConfig = RiskManagementConfig(
                atrPeriod = 2,
                initialStopMultiplier = BigDecimal("1"),
                trailingEnabled = false,
                trailingStopMultiplier = BigDecimal.ZERO,
            ),
        )

        assertEquals(1, result.trades.size)
        val trade = result.trades[0]
        assertEquals(ExitReason.SIGNAL, trade.exitReason)
        assertEquals(BigDecimal("110.0000"), trade.exitPrice)
    }

    @Test
    fun `a pending signal exit resolves before a same-bar stop breach is even checked`() {
        // The exit signal fires at the entry bar's own close (index 3), so it resolves at index
        // 4's open via step 1 - BEFORE step 1.5's stop check ever runs. Bar 4's low (50) is deep
        // below the stop (96), but the position is already FLAT by the time the check would run.
        val candles = listOf(
            candle(0, open = 100.0, high = 102.0, low = 98.0, close = 100.0),
            candle(1, open = 100.0, high = 102.0, low = 98.0, close = 100.0),
            candle(2, open = 100.0, high = 102.0, low = 98.0, close = 100.0),
            candle(3, open = 100.0, high = 102.0, low = 98.0, close = 100.0), // entry fill bar
            candle(4, open = 97.0, high = 98.0, low = 50.0, close = 60.0), // signal fills here
        )
        val strategy = ScriptedStrategy(mapOf(2 to StrategyAction.ENTER_LONG, 3 to StrategyAction.EXIT_LONG))

        val result = BacktestEngine.run(
            candles = candles,
            firstTradableIndex = 2,
            strategy = strategy,
            initialCapital = BigDecimal("1000"),
            quantity = BigDecimal("1"),
            commissionPerOrder = BigDecimal.ZERO,
            slippageBps = 0,
            riskManagementConfig = RiskManagementConfig(
                atrPeriod = 2,
                initialStopMultiplier = BigDecimal("1"),
                trailingEnabled = false,
                trailingStopMultiplier = BigDecimal.ZERO,
            ),
        )

        assertEquals(1, result.trades.size)
        val trade = result.trades[0]
        assertEquals(ExitReason.SIGNAL, trade.exitReason)
        assertEquals(BigDecimal("97.0000"), trade.exitPrice)
    }

    @Test
    fun `a stop breach on the literal final bar pre-empts the end-of-period force-close`() {
        val candles = listOf(
            candle(0, open = 100.0, high = 102.0, low = 98.0, close = 100.0),
            candle(1, open = 100.0, high = 102.0, low = 98.0, close = 100.0),
            candle(2, open = 100.0, high = 102.0, low = 98.0, close = 100.0),
            candle(3, open = 100.0, high = 102.0, low = 98.0, close = 100.0), // entry fill bar
            candle(4, open = 94.0, high = 96.0, low = 50.0, close = 93.0), // final bar, gap below stop
        )
        val strategy = ScriptedStrategy(mapOf(2 to StrategyAction.ENTER_LONG))

        val result = BacktestEngine.run(
            candles = candles,
            firstTradableIndex = 2,
            strategy = strategy,
            initialCapital = BigDecimal("1000"),
            quantity = BigDecimal("1"),
            commissionPerOrder = BigDecimal.ZERO,
            slippageBps = 0,
            riskManagementConfig = RiskManagementConfig(
                atrPeriod = 2,
                initialStopMultiplier = BigDecimal("1"),
                trailingEnabled = false,
                trailingStopMultiplier = BigDecimal.ZERO,
            ),
        )

        assertEquals(1, result.trades.size)
        val trade = result.trades[0]
        assertEquals(ExitReason.ATR_STOP, trade.exitReason)
        assertEquals(BigDecimal("94.0000"), trade.exitPrice)
    }

    @Test
    fun `a disabled trailing stop leaves the stop flat across the whole trade`() {
        val candles = listOf(
            candle(0, open = 100.0, high = 102.0, low = 98.0, close = 100.0),
            candle(1, open = 100.0, high = 102.0, low = 98.0, close = 100.0),
            candle(2, open = 100.0, high = 102.0, low = 98.0, close = 100.0),
            candle(3, open = 100.0, high = 102.0, low = 98.0, close = 100.0), // entry fill bar
            candle(4, open = 105.0, high = 110.0, low = 104.0, close = 108.0),
            candle(5, open = 108.0, high = 110.0, low = 106.0, close = 108.0), // final bar
        )
        val strategy = ScriptedStrategy(mapOf(2 to StrategyAction.ENTER_LONG))

        val result = BacktestEngine.run(
            candles = candles,
            firstTradableIndex = 2,
            strategy = strategy,
            initialCapital = BigDecimal("1000"),
            quantity = BigDecimal("1"),
            commissionPerOrder = BigDecimal.ZERO,
            slippageBps = 0,
            riskManagementConfig = RiskManagementConfig(
                atrPeriod = 2,
                initialStopMultiplier = BigDecimal("1"),
                trailingEnabled = false,
                trailingStopMultiplier = BigDecimal.ZERO,
            ),
        )

        val trade = result.trades[0]
        assertEquals(trade.initialStopPrice, trade.finalStopPrice)
        assertEquals(BigDecimal("96.0000"), trade.finalStopPrice)
    }

    @Test
    fun `an enabled trailing stop ratchets up on new highs and the exit uses the raised level`() {
        // period 1 (atrAt(i) = candle i's own true range, no smoothing lag - see the first ATR
        // test above for why). Hand-traced: TR(1)=2 -> atrAtSignal=2 -> initial stop = 100-1*2=98.
        // Day 3's own high (112) and TR(3)=9 ratchet the stop to max(98, 112-1*9)=103 for day 4
        // onward. Day 4's low (101) sits between the old stop (98) and the new one (103) - only
        // the ratcheted stop explains the trigger, and it must not have fallen back to 98.
        val candles = listOf(
            candle(0, open = 100.0, high = 100.0, low = 100.0, close = 100.0),
            candle(1, open = 100.0, high = 101.0, low = 99.0, close = 100.0), // TR=2, signal fires here
            candle(2, open = 100.0, high = 105.0, low = 98.0, close = 103.0), // entry fill bar
            candle(3, open = 106.0, high = 112.0, low = 104.0, close = 110.0), // new high, ratchets stop
            candle(4, open = 104.0, high = 106.0, low = 101.0, close = 102.0), // pullback, triggers ratcheted stop
        )
        val strategy = ScriptedStrategy(mapOf(1 to StrategyAction.ENTER_LONG))

        val result = BacktestEngine.run(
            candles = candles,
            firstTradableIndex = 1,
            strategy = strategy,
            initialCapital = BigDecimal("1000"),
            quantity = BigDecimal("1"),
            commissionPerOrder = BigDecimal.ZERO,
            slippageBps = 0,
            riskManagementConfig = RiskManagementConfig(
                atrPeriod = 1,
                initialStopMultiplier = BigDecimal("1"),
                trailingEnabled = true,
                trailingStopMultiplier = BigDecimal("1"),
            ),
        )

        assertEquals(1, result.trades.size)
        val trade = result.trades[0]
        assertEquals(BigDecimal("98.0000"), trade.initialStopPrice)
        assertEquals(BigDecimal("103.0000"), trade.finalStopPrice)
        assertEquals(ExitReason.ATR_STOP, trade.exitReason)
        assertEquals(BigDecimal("103.0000"), trade.exitPrice)
    }

    @Test
    fun `risk-based sizing computes quantity from the risk budget and floors it`() {
        // atrAtSignal=4 (the familiar flat day0-2 pattern), multiplier 1.5 -> riskPerUnit=6.
        // riskBudget = 10000 * 1% = 100. 100/6 = 16.6666... -> FLOOR at scale 8, never rounded up
        // past the declared budget.
        val candles = listOf(
            candle(0, open = 100.0, high = 102.0, low = 98.0, close = 100.0),
            candle(1, open = 100.0, high = 102.0, low = 98.0, close = 100.0),
            candle(2, open = 100.0, high = 102.0, low = 98.0, close = 100.0),
            candle(3, open = 100.0, high = 102.0, low = 98.0, close = 100.0), // entry fill bar
            candle(4, open = 100.0, high = 102.0, low = 98.0, close = 100.0), // final bar
        )
        val strategy = ScriptedStrategy(mapOf(2 to StrategyAction.ENTER_LONG))

        val result = BacktestEngine.run(
            candles = candles,
            firstTradableIndex = 2,
            strategy = strategy,
            initialCapital = BigDecimal("10000"),
            commissionPerOrder = BigDecimal.ZERO,
            slippageBps = 0,
            riskPercentPerTrade = BigDecimal("1"),
            riskManagementConfig = RiskManagementConfig(
                atrPeriod = 2,
                initialStopMultiplier = BigDecimal("1.5"),
                trailingEnabled = false,
                trailingStopMultiplier = BigDecimal.ZERO,
            ),
        )

        assertEquals(1, result.trades.size)
        assertEquals(BigDecimal("16.66666666"), result.trades[0].quantity)
        assertEquals(0, result.entriesSkippedInvalidQuantity)
    }

    @Test
    fun `a degenerate ATR or multiplier skips the entry as an invalid quantity, not an exception`() {
        val candles = listOf(
            candle(0, open = 100.0, high = 100.0, low = 100.0, close = 100.0),
            candle(1, open = 100.0, high = 100.0, low = 100.0, close = 100.0),
            candle(2, open = 100.0, high = 100.0, low = 100.0, close = 100.0),
            candle(3, open = 100.0, high = 100.0, low = 100.0, close = 100.0),
        )
        val strategy = ScriptedStrategy(mapOf(2 to StrategyAction.ENTER_LONG))

        val result = BacktestEngine.run(
            candles = candles,
            firstTradableIndex = 2,
            strategy = strategy,
            initialCapital = BigDecimal("10000"),
            commissionPerOrder = BigDecimal.ZERO,
            slippageBps = 0,
            riskPercentPerTrade = BigDecimal("1"),
            riskManagementConfig = RiskManagementConfig(
                atrPeriod = 2,
                initialStopMultiplier = BigDecimal("1"),
                trailingEnabled = false,
                trailingStopMultiplier = BigDecimal.ZERO,
            ),
        )

        assertTrue(result.trades.isEmpty())
        assertEquals(1, result.entrySignalsGenerated)
        assertEquals(1, result.entriesSkippedInvalidQuantity)
        assertEquals(0, result.entriesSkippedInsufficientCash)
    }

    @Test
    fun `a validly sized but unaffordable risk-based entry is a distinct skip reason from an invalid quantity`() {
        val candles = listOf(
            candle(0, open = 100.0, high = 102.0, low = 98.0, close = 100.0),
            candle(1, open = 100.0, high = 102.0, low = 98.0, close = 100.0),
            candle(2, open = 100.0, high = 102.0, low = 98.0, close = 100.0),
            candle(3, open = 100.0, high = 102.0, low = 98.0, close = 100.0),
        )
        val strategy = ScriptedStrategy(mapOf(2 to StrategyAction.ENTER_LONG))

        val result = BacktestEngine.run(
            candles = candles,
            firstTradableIndex = 2,
            strategy = strategy,
            initialCapital = BigDecimal("100"),
            commissionPerOrder = BigDecimal("200"), // dwarfs the tiny account, even for 0.25 units
            slippageBps = 0,
            riskPercentPerTrade = BigDecimal("1"),
            riskManagementConfig = RiskManagementConfig(
                atrPeriod = 2,
                initialStopMultiplier = BigDecimal("1"),
                trailingEnabled = false,
                trailingStopMultiplier = BigDecimal.ZERO,
            ),
        )

        assertTrue(result.trades.isEmpty())
        assertEquals(1, result.entrySignalsGenerated)
        assertEquals(0, result.entriesSkippedInvalidQuantity)
        assertEquals(1, result.entriesSkippedInsufficientCash)
    }

    @Test
    fun `quantity and riskPercentPerTrade are mutually exclusive`() {
        val candles = listOf(candle(0, open = 100.0, close = 100.0), candle(1, open = 100.0, close = 100.0))
        val strategy = ScriptedStrategy()

        assertThrows(IllegalArgumentException::class.java) {
            BacktestEngine.run(
                candles = candles,
                firstTradableIndex = 0,
                strategy = strategy,
                initialCapital = BigDecimal("1000"),
                quantity = BigDecimal("1"),
                commissionPerOrder = BigDecimal.ZERO,
                slippageBps = 0,
                riskPercentPerTrade = BigDecimal("1"),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            BacktestEngine.run(
                candles = candles,
                firstTradableIndex = 0,
                strategy = strategy,
                initialCapital = BigDecimal("1000"),
                commissionPerOrder = BigDecimal.ZERO,
                slippageBps = 0,
            )
        }
    }
}
