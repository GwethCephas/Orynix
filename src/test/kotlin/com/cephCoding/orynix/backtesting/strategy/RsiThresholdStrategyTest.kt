package com.cephCoding.orynix.backtesting.strategy

import com.cephCoding.orynix.backtesting.engine.StrategyAction
import com.cephCoding.orynix.market.domain.Candle
import com.cephCoding.orynix.market.domain.CandleTimeframe
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant

class RsiThresholdStrategyTest {

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
    fun `warmupPeriod equals rsiPeriod plus one`() {
        val candles = candlesFromCloses(listOf(50.0, 48.0, 46.0, 44.0, 46.0))
        val strategy = RsiThresholdStrategy(candles, rsiPeriod = 3, oversoldThreshold = BigDecimal("30"), overboughtThreshold = BigDecimal("70"))
        assertEquals(4, strategy.warmupPeriod)
    }

    @Test
    fun `enters on a confirmed bounce from oversold and exits on a confirmed rollover from overbought`() {
        // hand-computed via Rsi.calculate's own Wilder-smoothing algorithm (period 3) over
        // [50,48,46,44,46,50,55,50]: RSI dips to 0 at index 3, bounces to 33.33 at index 4
        // (crossing up through 30 -> confirmed bounce), climbs through the overbought zone to
        // 82.80 at index 6, then rolls over to 47.97 at index 7 (crossing down through 70 ->
        // confirmed rollover). Merely entering the overbought zone (index 6) is not itself a
        // signal under this strategy's semantics.
        val candles = candlesFromCloses(listOf(50.0, 48.0, 46.0, 44.0, 46.0, 50.0, 55.0, 50.0))
        val strategy = RsiThresholdStrategy(candles, rsiPeriod = 3, oversoldThreshold = BigDecimal("30"), overboughtThreshold = BigDecimal("70"))

        assertEquals(StrategyAction.ENTER_LONG, strategy.signalAt(4, candles))
        assertEquals(StrategyAction.HOLD, strategy.signalAt(5, candles))
        assertEquals(StrategyAction.HOLD, strategy.signalAt(6, candles))
        assertEquals(StrategyAction.EXIT_LONG, strategy.signalAt(7, candles))
    }
}
