package com.cephCoding.orynix.backtesting.engine

import com.cephCoding.orynix.backtesting.domain.ExitReason
import com.cephCoding.orynix.backtesting.strategy.BacktestStrategy
import com.cephCoding.orynix.market.domain.Candle
import com.cephCoding.orynix.market.domain.CandleTimeframe
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
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
}
