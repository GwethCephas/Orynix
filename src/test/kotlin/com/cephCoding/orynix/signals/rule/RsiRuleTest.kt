package com.cephCoding.orynix.signals.rule

import com.cephCoding.orynix.market.domain.CandleTimeframe
import com.cephCoding.orynix.technicalanalysis.domain.BollingerBands
import com.cephCoding.orynix.technicalanalysis.domain.Macd
import com.cephCoding.orynix.technicalanalysis.domain.TechnicalAnalysisResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant

class RsiRuleTest {

    private fun result(rsi14: String) = TechnicalAnalysisResult(
        symbol = "TEST",
        timeframe = CandleTimeframe.DAILY,
        timestamp = Instant.parse("2026-01-01T00:00:00Z"),
        sma20 = BigDecimal("100.00"),
        sma50 = BigDecimal("100.00"),
        ema20 = BigDecimal("100.00"),
        rsi14 = BigDecimal(rsi14),
        macd = Macd(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO),
        bollingerBands = BollingerBands(BigDecimal("110.00"), BigDecimal("100.00"), BigDecimal("90.00")),
        atr14 = BigDecimal("2.00"),
        volumeSma20 = BigDecimal("1000000.00"),
        volumeChangePercent = null,
    )

    @Test
    fun `overbought at and above 70`() {
        assertEquals(RsiState.OVERBOUGHT, RsiRule.evaluate(result("70.00"), 70.0, 30.0))
        assertEquals(RsiState.OVERBOUGHT, RsiRule.evaluate(result("85.00"), 70.0, 30.0))
    }

    @Test
    fun `oversold at and below 30`() {
        assertEquals(RsiState.OVERSOLD, RsiRule.evaluate(result("30.00"), 70.0, 30.0))
        assertEquals(RsiState.OVERSOLD, RsiRule.evaluate(result("15.00"), 70.0, 30.0))
    }

    @Test
    fun `neutral strictly between the thresholds`() {
        assertEquals(RsiState.NEUTRAL, RsiRule.evaluate(result("69.99"), 70.0, 30.0))
        assertEquals(RsiState.NEUTRAL, RsiRule.evaluate(result("30.01"), 70.0, 30.0))
        assertEquals(RsiState.NEUTRAL, RsiRule.evaluate(result("50.00"), 70.0, 30.0))
    }
}
