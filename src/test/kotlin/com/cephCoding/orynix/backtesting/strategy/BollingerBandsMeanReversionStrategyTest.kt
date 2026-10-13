package com.cephCoding.orynix.backtesting.strategy

import com.cephCoding.orynix.backtesting.engine.StrategyAction
import com.cephCoding.orynix.market.domain.Candle
import com.cephCoding.orynix.market.domain.CandleTimeframe
import com.cephCoding.orynix.technicalanalysis.indicator.BollingerBandsCalculator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant

class BollingerBandsMeanReversionStrategyTest {

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
    fun `warmupPeriod equals the lookback period`() {
        val strategy = BollingerBandsMeanReversionStrategy(candlesFromCloses(listOf(1.0, 2.0, 3.0, 4.0, 5.0)), period = 5, stdDevMultiplier = 2.0)
        assertEquals(5, strategy.warmupPeriod)
    }

    @Test
    fun `middle, upper, and lower bands match BollingerBandsCalculator directly`() {
        val closes = listOf(100.0, 102.0, 98.0, 101.0, 99.0)
        val series = BollingerBandsCalculator.calculate(closes, period = 5, stdDevMultiplier = 1.0)

        // Sma(5) of [100,102,98,101,99] = 100.0; population std dev = sqrt(mean of squared
        // deviations [0,4,4,1,1] -> 10/5=2.0) = sqrt(2.0).
        val expectedMiddle = 100.0
        val expectedStdDev = Math.sqrt(2.0)
        assertEquals(expectedMiddle, series.middle[0], 1e-9)
        assertEquals(expectedMiddle + expectedStdDev, series.upper[0], 1e-9)
        assertEquals(expectedMiddle - expectedStdDev, series.lower[0], 1e-9)
    }

    @Test
    fun `enters on a confirmed breakdown below the lower band, holds through recovery, and exits on reversion above the middle band`() {
        // hand-verified by running BollingerBandsCalculator's own algorithm (period 5, multiplier
        // 1.0) over this series: close dips to 85 at index 5, below the lower band (90.8356) for
        // the first time (a confirmed breakdown, not merely "already below") -> ENTER_LONG. At
        // index 6 close recovers to 90, back above the lower band (88.514) but still below the
        // middle band (94.6) -> HOLD, not a repeated entry. At index 7 close reaches 101, crossing
        // above the middle band (95.2) -> EXIT_LONG (mean-reversion target reached).
        val closes = listOf(100.0, 102.0, 98.0, 101.0, 99.0, 85.0, 90.0, 101.0, 100.0, 100.0)
        val candles = candlesFromCloses(closes)
        val strategy = BollingerBandsMeanReversionStrategy(candles, period = 5, stdDevMultiplier = 1.0)

        assertEquals(StrategyAction.ENTER_LONG, strategy.signalAt(5, candles))
        assertEquals(StrategyAction.HOLD, strategy.signalAt(6, candles))
        assertEquals(StrategyAction.EXIT_LONG, strategy.signalAt(7, candles))
        assertEquals(StrategyAction.HOLD, strategy.signalAt(8, candles))
    }

    @Test
    fun `no signal is evaluated before the lookback period has elapsed`() {
        val closes = listOf(100.0, 102.0, 98.0, 101.0, 99.0, 85.0)
        val candles = candlesFromCloses(closes)
        val strategy = BollingerBandsMeanReversionStrategy(candles, period = 5, stdDevMultiplier = 1.0)

        assertEquals(StrategyAction.HOLD, strategy.signalAt(0, candles))
        assertEquals(StrategyAction.HOLD, strategy.signalAt(4, candles))
    }
}
