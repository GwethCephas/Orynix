package com.cephCoding.orynix.signals.rule

import com.cephCoding.orynix.market.domain.CandleTimeframe
import com.cephCoding.orynix.technicalanalysis.domain.BollingerBands
import com.cephCoding.orynix.technicalanalysis.domain.Macd
import com.cephCoding.orynix.technicalanalysis.domain.TechnicalAnalysisResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant

class VolatilityRuleTest {

    private fun result(atr14: String) = TechnicalAnalysisResult(
        symbol = "TEST",
        timeframe = CandleTimeframe.DAILY,
        timestamp = Instant.parse("2026-01-01T00:00:00Z"),
        sma20 = BigDecimal("100.00"),
        sma50 = BigDecimal("100.00"),
        ema20 = BigDecimal("100.00"),
        rsi14 = BigDecimal("50.00"),
        macd = Macd(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO),
        bollingerBands = BollingerBands(BigDecimal("110.00"), BigDecimal("100.00"), BigDecimal("90.00")),
        atr14 = BigDecimal(atr14),
        volumeSma20 = BigDecimal("1000000.00"),
        volumeChangePercent = null,
    )

    private val closePrice = BigDecimal("100.00")

    @Test
    fun `low volatility below the low threshold`() {
        val assessment = VolatilityRule.evaluate(result("0.50"), closePrice, lowPercent = 1.0, highPercent = 3.0)
        assertEquals(VolatilityState.LOW, assessment.state)
    }

    @Test
    fun `normal volatility between the thresholds`() {
        val assessment = VolatilityRule.evaluate(result("2.00"), closePrice, lowPercent = 1.0, highPercent = 3.0)
        assertEquals(VolatilityState.NORMAL, assessment.state)
    }

    @Test
    fun `high volatility above the high threshold`() {
        val assessment = VolatilityRule.evaluate(result("4.00"), closePrice, lowPercent = 1.0, highPercent = 3.0)
        assertEquals(VolatilityState.HIGH, assessment.state)
    }

    @Test
    fun `reports the computed ATR percent alongside the state`() {
        val assessment = VolatilityRule.evaluate(result("2.00"), closePrice, lowPercent = 1.0, highPercent = 3.0)
        assertEquals(2.0, assessment.atrPercent, 1e-9)
    }
}
