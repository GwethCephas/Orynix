package com.cephCoding.orynix.signals.rule

import com.cephCoding.orynix.market.domain.CandleTimeframe
import com.cephCoding.orynix.technicalanalysis.domain.BollingerBands
import com.cephCoding.orynix.technicalanalysis.domain.Macd
import com.cephCoding.orynix.technicalanalysis.domain.TechnicalAnalysisResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant

class TrendRuleTest {

    private fun result(ema20: String, sma20: String, sma50: String) = TechnicalAnalysisResult(
        symbol = "TEST",
        timeframe = CandleTimeframe.DAILY,
        timestamp = Instant.parse("2026-01-01T00:00:00Z"),
        sma20 = BigDecimal(sma20),
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
    fun `bullish when EMA20 greater than SMA20 greater than SMA50`() {
        assertEquals(TrendState.BULLISH, TrendRule.evaluate(result(ema20 = "105", sma20 = "103", sma50 = "100")))
    }

    @Test
    fun `bearish when EMA20 less than SMA20 less than SMA50`() {
        assertEquals(TrendState.BEARISH, TrendRule.evaluate(result(ema20 = "95", sma20 = "97", sma50 = "100")))
    }

    @Test
    fun `neutral for any other ordering`() {
        assertEquals(TrendState.NEUTRAL, TrendRule.evaluate(result(ema20 = "105", sma20 = "100", sma50 = "103")))
    }
}
