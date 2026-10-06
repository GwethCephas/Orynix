package com.cephCoding.orynix.signals.rule

import com.cephCoding.orynix.market.domain.Candle
import com.cephCoding.orynix.market.domain.CandleTimeframe
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit

class PriceMomentumRuleTest {

    private fun candles(closes: List<Double>): List<Candle> {
        val base = Instant.parse("2026-01-01T00:00:00Z")
        return closes.mapIndexed { i, close ->
            Candle(
                symbol = "TEST",
                timeframe = CandleTimeframe.DAILY,
                timestamp = base.plus(i.toLong(), ChronoUnit.DAYS),
                open = BigDecimal.valueOf(close),
                high = BigDecimal.valueOf(close),
                low = BigDecimal.valueOf(close),
                close = BigDecimal.valueOf(close),
                volume = BigDecimal.valueOf(1_000_000.0),
            )
        }
    }

    @Test
    fun `positive momentum when price rose past the flat band`() {
        val series = candles(List(10) { 100.0 } + listOf(105.0))
        val assessment = PriceMomentumRule.evaluate(series, lookback = 10, flatPercent = 1.0)
        assertEquals(PriceMomentumState.POSITIVE, assessment.state)
    }

    @Test
    fun `negative momentum when price fell past the flat band`() {
        val series = candles(List(10) { 100.0 } + listOf(95.0))
        val assessment = PriceMomentumRule.evaluate(series, lookback = 10, flatPercent = 1.0)
        assertEquals(PriceMomentumState.NEGATIVE, assessment.state)
    }

    @Test
    fun `flat when the change stays within the flat band`() {
        val series = candles(List(10) { 100.0 } + listOf(100.5))
        val assessment = PriceMomentumRule.evaluate(series, lookback = 10, flatPercent = 1.0)
        assertEquals(PriceMomentumState.FLAT, assessment.state)
    }

    @Test
    fun `reports the computed change percent alongside the state`() {
        val series = candles(List(10) { 100.0 } + listOf(105.0))
        val assessment = PriceMomentumRule.evaluate(series, lookback = 10, flatPercent = 1.0)
        assertEquals(5.0, assessment.changePercent, 1e-9)
    }
}
