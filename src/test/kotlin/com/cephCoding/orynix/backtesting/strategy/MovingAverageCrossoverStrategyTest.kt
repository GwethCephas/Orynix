package com.cephCoding.orynix.backtesting.strategy

import com.cephCoding.orynix.backtesting.engine.StrategyAction
import com.cephCoding.orynix.market.domain.Candle
import com.cephCoding.orynix.market.domain.CandleTimeframe
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant

class MovingAverageCrossoverStrategyTest {

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
    fun `warmupPeriod equals slowPeriod`() {
        val strategy = MovingAverageCrossoverStrategy(candlesFromCloses(listOf(1.0, 2.0, 3.0, 4.0)), fastPeriod = 2, slowPeriod = 3)
        assertEquals(3, strategy.warmupPeriod)
    }

    @Test
    fun `enters only on the actual bullish crossover and exits only on the actual bearish crossover`() {
        // hand-computed: Sma(period 2)/Sma(period 3) over [100,100,100,130,60] put fast
        // at-or-below slow through index 2, strictly above at index 3 (bullish crossover),
        // then strictly below again at index 4 (bearish crossover).
        val candles = candlesFromCloses(listOf(100.0, 100.0, 100.0, 130.0, 60.0))
        val strategy = MovingAverageCrossoverStrategy(candles, fastPeriod = 2, slowPeriod = 3)

        assertEquals(StrategyAction.HOLD, strategy.signalAt(2, candles))
        assertEquals(StrategyAction.ENTER_LONG, strategy.signalAt(3, candles))
        assertEquals(StrategyAction.EXIT_LONG, strategy.signalAt(4, candles))
    }

    @Test
    fun `already above the slow average is not reported as a new crossover`() {
        val candles = candlesFromCloses(listOf(50.0, 100.0, 150.0, 200.0, 250.0, 300.0))
        val strategy = MovingAverageCrossoverStrategy(candles, fastPeriod = 2, slowPeriod = 3)

        assertEquals(StrategyAction.HOLD, strategy.signalAt(3, candles))
        assertEquals(StrategyAction.HOLD, strategy.signalAt(4, candles))
        assertEquals(StrategyAction.HOLD, strategy.signalAt(5, candles))
    }
}
