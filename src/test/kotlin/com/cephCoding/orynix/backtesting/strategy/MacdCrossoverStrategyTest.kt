package com.cephCoding.orynix.backtesting.strategy

import com.cephCoding.orynix.backtesting.engine.StrategyAction
import com.cephCoding.orynix.market.domain.Candle
import com.cephCoding.orynix.market.domain.CandleTimeframe
import com.cephCoding.orynix.technicalanalysis.indicator.MacdCalculator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant

class MacdCrossoverStrategyTest {

    private fun candlesFromCloses(closes: List<Double>): List<Candle> = closes.mapIndexed { i, close ->
        Candle(
            symbol = "TEST",
            timeframe = CandleTimeframe.DAILY,
            timestamp = Instant.parse("2026-01-01T00:00:00Z").plusSeconds(i * 86_400L),
            open = BigDecimal.valueOf(close),
            high = BigDecimal.valueOf(close),
            low = BigDecimal.valueOf(close),
            close = BigDecimal.valueOf(close),
            volume = BigDecimal.valueOf(1_000.0),
        )
    }

    @Test
    fun `warmupPeriod equals slowPeriod plus signalPeriod minus one`() {
        val strategy = MacdCrossoverStrategy(candlesFromCloses(listOf(1.0, 2.0, 3.0, 4.0, 5.0)), fastPeriod = 2, slowPeriod = 3, signalPeriod = 2)
        assertEquals(4, strategy.warmupPeriod)
    }

    @Test
    fun `fast EMA, slow EMA, and MACD line match MacdCalculator directly`() {
        val closes = listOf(10.0, 10.0, 10.0, 10.0, 100.0, 90.0, 10.0, 10.0)
        val series = MacdCalculator.calculate(closes, fastPeriod = 2, slowPeriod = 3, signalPeriod = 2)

        // Ema(2) seeds at the average of the first 2 closes (10,10 -> 10), Ema(3) seeds at the
        // average of the first 3 (10,10,10 -> 10); both stay flat at 10 until the spike at index 4.
        assertEquals(15.0, series.macdLine[2], 1e-6) // original index 4: fastEma(70) - slowEma(55)
        assertEquals(10.0, series.signalLine[1], 1e-6) // original index 4: signal EMA seeds at 0 (avg of the first two zero macdLine values), then recurses to 0 + (2/3)*(15-0) = 10.0
    }

    @Test
    fun `enters on a confirmed bullish crossover, holds while above, and exits on the bearish crossover`() {
        // hand-verified by running MacdCalculator's own EMA/seeding algorithm (fast 2, slow 3,
        // signal 2) over this series: MACD crosses from at-or-below the signal line (both exactly
        // equal at index 3) to above it at index 4 -> ENTER_LONG. It remains above (10.83 vs
        // 10.56) at index 5 -> HOLD, not a repeated entry. It crosses back below (-6.81 vs -1.02)
        // at index 6 -> EXIT_LONG.
        val closes = listOf(10.0, 10.0, 10.0, 10.0, 100.0, 90.0, 10.0, 10.0)
        val candles = candlesFromCloses(closes)
        val strategy = MacdCrossoverStrategy(candles, fastPeriod = 2, slowPeriod = 3, signalPeriod = 2)

        assertEquals(StrategyAction.ENTER_LONG, strategy.signalAt(4, candles))
        assertEquals(StrategyAction.HOLD, strategy.signalAt(5, candles))
        assertEquals(StrategyAction.EXIT_LONG, strategy.signalAt(6, candles))
        assertEquals(StrategyAction.HOLD, strategy.signalAt(7, candles))
    }

    @Test
    fun `no signal is evaluated before warmup has elapsed`() {
        val closes = listOf(10.0, 10.0, 10.0, 10.0)
        val candles = candlesFromCloses(closes)
        val strategy = MacdCrossoverStrategy(candles, fastPeriod = 2, slowPeriod = 3, signalPeriod = 2)

        assertEquals(StrategyAction.HOLD, strategy.signalAt(0, candles))
        assertEquals(StrategyAction.HOLD, strategy.signalAt(3, candles))
    }
}
