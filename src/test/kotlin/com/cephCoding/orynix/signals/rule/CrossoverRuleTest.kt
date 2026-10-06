package com.cephCoding.orynix.signals.rule

import com.cephCoding.orynix.market.domain.CandleTimeframe
import com.cephCoding.orynix.technicalanalysis.domain.BollingerBands
import com.cephCoding.orynix.technicalanalysis.domain.Macd
import com.cephCoding.orynix.technicalanalysis.domain.TechnicalAnalysisResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant

class CrossoverRuleTest {

    private fun result(ema20: String, sma50: String) = TechnicalAnalysisResult(
        symbol = "TEST",
        timeframe = CandleTimeframe.DAILY,
        timestamp = Instant.parse("2026-01-01T00:00:00Z"),
        sma20 = BigDecimal("100.00"),
        sma50 = BigDecimal(sma50),
        ema20 = BigDecimal(ema20),
        rsi14 = BigDecimal("50.00"),
        macd = Macd(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO),
        bollingerBands = BollingerBands(BigDecimal("110.00"), BigDecimal("100.00"), BigDecimal("90.00")),
        atr14 = BigDecimal("2.00"),
        volumeSma20 = BigDecimal("1000000.00"),
        volumeChangePercent = null,
    )

    @Test
    fun `bullish crossover when EMA20 moves from below to above SMA50`() {
        val previous = result(ema20 = "95", sma50 = "100")
        val current = result(ema20 = "101", sma50 = "100")
        assertEquals(CrossoverState.BULLISH_CROSSOVER, CrossoverRule.evaluate(current, previous))
    }

    @Test
    fun `bearish crossover when EMA20 moves from above to below SMA50`() {
        val previous = result(ema20 = "105", sma50 = "100")
        val current = result(ema20 = "99", sma50 = "100")
        assertEquals(CrossoverState.BEARISH_CROSSOVER, CrossoverRule.evaluate(current, previous))
    }

    @Test
    fun `none when already above in both observations`() {
        val previous = result(ema20 = "102", sma50 = "100")
        val current = result(ema20 = "105", sma50 = "100")
        assertEquals(CrossoverState.NONE, CrossoverRule.evaluate(current, previous))
    }

    @Test
    fun `none when already below in both observations`() {
        val previous = result(ema20 = "98", sma50 = "100")
        val current = result(ema20 = "95", sma50 = "100")
        assertEquals(CrossoverState.NONE, CrossoverRule.evaluate(current, previous))
    }
}
