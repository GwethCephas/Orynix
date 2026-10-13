package com.cephCoding.orynix.backtesting.strategy

import com.cephCoding.orynix.backtesting.engine.StrategyAction
import com.cephCoding.orynix.market.domain.Candle
import com.cephCoding.orynix.market.domain.CandleTimeframe
import com.cephCoding.orynix.technicalanalysis.indicator.DonchianCalculator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant

class DonchianChannelBreakoutStrategyTest {

    private fun candle(i: Int, high: Double, low: Double, close: Double): Candle = Candle(
        symbol = "TEST",
        timeframe = CandleTimeframe.DAILY,
        timestamp = Instant.parse("2026-01-01T00:00:00Z").plusSeconds(i * 86_400L),
        open = BigDecimal.valueOf(close),
        high = BigDecimal.valueOf(high),
        low = BigDecimal.valueOf(low),
        close = BigDecimal.valueOf(close),
        volume = BigDecimal.valueOf(1_000.0),
    )

    @Test
    fun `warmupPeriod equals the longer of the two lookback periods plus one`() {
        val candles = (0..5).map { candle(it, 100.0, 100.0, 100.0) }
        val strategy = DonchianChannelBreakoutStrategy(candles, entryLookback = 3, exitLookback = 2)
        assertEquals(4, strategy.warmupPeriod)
    }

    @Test
    fun `upper and lower channels exclude the current candle and match DonchianCalculator directly`() {
        val highs = listOf(100.0, 101.0, 99.0, 102.0)
        val lows = listOf(98.0, 99.0, 97.0, 100.0)
        val series = DonchianCalculator.calculate(highs, lows, period = 3)

        // upper[0]/lower[0] correspond to original index 3, computed from highs/lows[0..2] -
        // the candle at index 3 (high=102, low=100) is deliberately excluded.
        assertEquals(101.0, series.upper[0], 1e-9) // max(100, 101, 99)
        assertEquals(97.0, series.lower[0], 1e-9) // min(98, 99, 97)
    }

    @Test
    fun `enters on a confirmed breakout above the entry channel, holds while above, and exits on breakdown below the exit channel`() {
        // hand-verified by running DonchianCalculator's own algorithm (entry lookback 3, exit
        // lookback 2) over this series: close breaks above the preceding 3-bar high channel (102)
        // for the first time at index 4 (close 120) -> ENTER_LONG. It remains above the (now
        // shifted-up) entry channel (125) at index 5 -> HOLD, not a repeated entry. At index 6 it
        // breaks below the preceding 2-bar low exit channel (112) -> EXIT_LONG.
        val highs = listOf(100.0, 101.0, 99.0, 102.0, 125.0, 118.0, 110.0)
        val lows = listOf(98.0, 99.0, 97.0, 100.0, 118.0, 112.0, 104.0)
        val closes = listOf(99.0, 100.0, 98.0, 101.0, 120.0, 115.0, 107.0)
        val candles = highs.indices.map { i -> candle(i, highs[i], lows[i], closes[i]) }
        val strategy = DonchianChannelBreakoutStrategy(candles, entryLookback = 3, exitLookback = 2)

        assertEquals(StrategyAction.ENTER_LONG, strategy.signalAt(4, candles))
        assertEquals(StrategyAction.HOLD, strategy.signalAt(5, candles))
        assertEquals(StrategyAction.EXIT_LONG, strategy.signalAt(6, candles))
    }

    @Test
    fun `no signal is evaluated before warmup has elapsed`() {
        val candles = (0..3).map { candle(it, 100.0 + it, 98.0 + it, 99.0 + it) }
        val strategy = DonchianChannelBreakoutStrategy(candles, entryLookback = 3, exitLookback = 2)

        assertEquals(StrategyAction.HOLD, strategy.signalAt(0, candles))
        assertEquals(StrategyAction.HOLD, strategy.signalAt(3, candles))
    }
}
