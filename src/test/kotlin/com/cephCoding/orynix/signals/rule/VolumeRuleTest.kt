package com.cephCoding.orynix.signals.rule

import com.cephCoding.orynix.market.domain.CandleTimeframe
import com.cephCoding.orynix.technicalanalysis.domain.BollingerBands
import com.cephCoding.orynix.technicalanalysis.domain.Macd
import com.cephCoding.orynix.technicalanalysis.domain.TechnicalAnalysisResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant

class VolumeRuleTest {

    private fun result(volumeSma20: String) = TechnicalAnalysisResult(
        symbol = "TEST",
        timeframe = CandleTimeframe.DAILY,
        timestamp = Instant.parse("2026-01-01T00:00:00Z"),
        sma20 = BigDecimal("100.00"),
        sma50 = BigDecimal("100.00"),
        ema20 = BigDecimal("100.00"),
        rsi14 = BigDecimal("50.00"),
        macd = Macd(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO),
        bollingerBands = BollingerBands(BigDecimal("110.00"), BigDecimal("100.00"), BigDecimal("90.00")),
        atr14 = BigDecimal("2.00"),
        volumeSma20 = BigDecimal(volumeSma20),
        volumeChangePercent = null,
    )

    @Test
    fun `high volume at and above the high ratio`() {
        val assessment = VolumeRule.evaluate(result("1000000"), BigDecimal("1500000"), highRatio = 1.5, lowRatio = 0.5)
        assertEquals(VolumeState.HIGH, assessment.state)
        assertEquals(1.5, assessment.ratio!!, 1e-9)
    }

    @Test
    fun `low volume at and below the low ratio`() {
        val assessment = VolumeRule.evaluate(result("1000000"), BigDecimal("500000"), highRatio = 1.5, lowRatio = 0.5)
        assertEquals(VolumeState.LOW, assessment.state)
    }

    @Test
    fun `normal volume between the ratios`() {
        val assessment = VolumeRule.evaluate(result("1000000"), BigDecimal("1000000"), highRatio = 1.5, lowRatio = 0.5)
        assertEquals(VolumeState.NORMAL, assessment.state)
    }

    @Test
    fun `zero volume SMA is undefined, not an exception`() {
        val assessment = VolumeRule.evaluate(result("0"), BigDecimal("500000"), highRatio = 1.5, lowRatio = 0.5)
        assertEquals(VolumeState.NORMAL, assessment.state)
        assertNull(assessment.ratio)
    }
}
