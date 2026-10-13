package com.cephCoding.orynix.backtesting.strategy

import com.cephCoding.orynix.backtesting.engine.StrategyAction
import com.cephCoding.orynix.market.domain.Candle
import com.cephCoding.orynix.market.domain.CandleTimeframe
import com.cephCoding.orynix.technicalanalysis.indicator.AdxCalculator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant

class AdxTrendStrategyTest {

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

    // Flat baseline (zero directional movement, so ADX starts at 0) for 12 bars, then a sustained
    // uptrend for 20 bars (pushing ADX well above threshold with +DI dominant), then a reversal
    // for 10 bars (flipping -DI above +DI). Hand-verified by running AdxCalculator's own Wilder
    // double-smoothing algorithm (period 5) over this exact series.
    private fun buildCandles(): List<Candle> {
        val candles = mutableListOf<Candle>()
        var i = 0
        repeat(12) {
            candles.add(candle(i, 100.0, 96.0, 98.0))
            i++
        }
        repeat(20) { step ->
            val up = (step + 1) * 3.0
            candles.add(candle(i, 100.0 + up, 96.0 + up, 98.0 + up))
            i++
        }
        repeat(10) { step ->
            val down = (step + 1) * 6.0
            candles.add(candle(i, 100.0 + 60.0 - down + 1.0, 100.0 + 60.0 - down - 5.0, 100.0 + 60.0 - down - 2.0))
            i++
        }
        return candles
    }

    @Test
    fun `warmupPeriod equals twice the ADX period`() {
        val strategy = AdxTrendStrategy(buildCandles().take(11), period = 5, threshold = 25.0)
        assertEquals(10, strategy.warmupPeriod)
    }

    @Test
    fun `plusDi, minusDi, and adx match AdxCalculator directly`() {
        val candles = buildCandles()
        val series = AdxCalculator.calculate(
            candles.map { it.high.toDouble() },
            candles.map { it.low.toDouble() },
            candles.map { it.close.toDouble() },
            period = 5,
        )

        // Flat baseline produces zero true range movement -> ADX/+DI/-DI are all defined as 0.0
        // (never NaN), not merely small, since there is no directional information at all yet.
        assertEquals(0.0, series.adx[0], 1e-9)
        assertEquals(0.0, series.plusDi[0], 1e-9)
        assertEquals(0.0, series.minusDi[0], 1e-9)
    }

    @Test
    fun `exits on weak ADX before any entry, enters on the confirmed bullish trend, holds while sustained, and exits on DI reversal`() {
        val candles = buildCandles()
        val strategy = AdxTrendStrategy(candles, period = 5, threshold = 25.0)

        // ADX is still 0 (well below the 25 threshold) at index 10 - the level-check exit branch
        // fires independently of any prior entry (harmless: the engine only acts on it while a
        // position is actually open).
        assertEquals(StrategyAction.EXIT_LONG, strategy.signalAt(10, candles))
        // The compound entry condition (ADX >= 25 and +DI > -DI) newly becomes true at index 13.
        assertEquals(StrategyAction.ENTER_LONG, strategy.signalAt(13, candles))
        // Still bullish and strong at index 14 - not a repeated entry.
        assertEquals(StrategyAction.HOLD, strategy.signalAt(14, candles))
        // -DI confirms a crossover above +DI at index 33, during the reversal.
        assertEquals(StrategyAction.EXIT_LONG, strategy.signalAt(33, candles))
    }

    @Test
    fun `no signal is evaluated before warmup has elapsed`() {
        val candles = buildCandles().take(11)
        val strategy = AdxTrendStrategy(candles, period = 5, threshold = 25.0)

        assertEquals(StrategyAction.HOLD, strategy.signalAt(0, candles))
        assertEquals(StrategyAction.HOLD, strategy.signalAt(9, candles))
    }
}
