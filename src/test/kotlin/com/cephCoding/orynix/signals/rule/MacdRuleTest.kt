package com.cephCoding.orynix.signals.rule

import com.cephCoding.orynix.market.domain.CandleTimeframe
import com.cephCoding.orynix.technicalanalysis.domain.BollingerBands
import com.cephCoding.orynix.technicalanalysis.domain.Macd
import com.cephCoding.orynix.technicalanalysis.domain.TechnicalAnalysisResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant

class MacdRuleTest {

    private fun result(macd: String, signal: String) = TechnicalAnalysisResult(
        symbol = "TEST",
        timeframe = CandleTimeframe.DAILY,
        timestamp = Instant.parse("2026-01-01T00:00:00Z"),
        sma20 = BigDecimal("100.00"),
        sma50 = BigDecimal("100.00"),
        ema20 = BigDecimal("100.00"),
        rsi14 = BigDecimal("50.00"),
        macd = Macd(macd = BigDecimal(macd), signal = BigDecimal(signal), histogram = BigDecimal(macd) - BigDecimal(signal)),
        bollingerBands = BollingerBands(BigDecimal("110.00"), BigDecimal("100.00"), BigDecimal("90.00")),
        atr14 = BigDecimal("2.00"),
        volumeSma20 = BigDecimal("1000000.00"),
        volumeChangePercent = null,
    )

    @Test
    fun `bullish crossover when MACD moves from at-or-below to above signal`() {
        val previous = result(macd = "0.50", signal = "1.00")
        val current = result(macd = "1.50", signal = "1.00")
        assertEquals(MacdState.BULLISH_CROSSOVER, MacdRule.evaluate(current, previous))
    }

    @Test
    fun `bearish crossover when MACD moves from above to at-or-below signal`() {
        val previous = result(macd = "1.50", signal = "1.00")
        val current = result(macd = "0.50", signal = "1.00")
        assertEquals(MacdState.BEARISH_CROSSOVER, MacdRule.evaluate(current, previous))
    }

    @Test
    fun `bullish momentum when no crossover and histogram is positive`() {
        val previous = result(macd = "1.50", signal = "1.00")
        val current = result(macd = "1.80", signal = "1.00")
        assertEquals(MacdState.BULLISH_MOMENTUM, MacdRule.evaluate(current, previous))
    }

    @Test
    fun `bearish momentum when no crossover and histogram is negative`() {
        val previous = result(macd = "0.50", signal = "1.00")
        val current = result(macd = "0.20", signal = "1.00")
        assertEquals(MacdState.BEARISH_MOMENTUM, MacdRule.evaluate(current, previous))
    }

    @Test
    fun `neutral momentum when no crossover and histogram is exactly zero`() {
        val previous = result(macd = "0.50", signal = "1.00")
        val current = result(macd = "1.00", signal = "1.00")
        assertEquals(MacdState.NEUTRAL_MOMENTUM, MacdRule.evaluate(current, previous))
    }
}
