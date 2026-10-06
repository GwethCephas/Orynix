package com.cephCoding.orynix.signals.rule

import com.cephCoding.orynix.market.domain.CandleTimeframe
import com.cephCoding.orynix.technicalanalysis.domain.BollingerBands
import com.cephCoding.orynix.technicalanalysis.domain.Macd
import com.cephCoding.orynix.technicalanalysis.domain.TechnicalAnalysisResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant

class BollingerBandsRuleTest {

    private fun result(upper: String, lower: String) = TechnicalAnalysisResult(
        symbol = "TEST",
        timeframe = CandleTimeframe.DAILY,
        timestamp = Instant.parse("2026-01-01T00:00:00Z"),
        sma20 = BigDecimal("100.00"),
        sma50 = BigDecimal("100.00"),
        ema20 = BigDecimal("100.00"),
        rsi14 = BigDecimal("50.00"),
        macd = Macd(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO),
        bollingerBands = BollingerBands(upper = BigDecimal(upper), middle = BigDecimal("100.00"), lower = BigDecimal(lower)),
        atr14 = BigDecimal("2.00"),
        volumeSma20 = BigDecimal("1000000.00"),
        volumeChangePercent = null,
    )

    private val fixture = result(upper = "110.00", lower = "90.00")

    @Test
    fun `price above upper band`() {
        assertEquals(
            BollingerBandsState.PRICE_ABOVE_UPPER_BAND,
            BollingerBandsRule.evaluate(fixture, BigDecimal("111.00"), nearPercent = 1.0),
        )
    }

    @Test
    fun `price below lower band`() {
        assertEquals(
            BollingerBandsState.PRICE_BELOW_LOWER_BAND,
            BollingerBandsRule.evaluate(fixture, BigDecimal("89.00"), nearPercent = 1.0),
        )
    }

    @Test
    fun `price near upper band`() {
        assertEquals(
            BollingerBandsState.PRICE_NEAR_UPPER_BAND,
            BollingerBandsRule.evaluate(fixture, BigDecimal("109.50"), nearPercent = 1.0),
        )
    }

    @Test
    fun `price near lower band`() {
        assertEquals(
            BollingerBandsState.PRICE_NEAR_LOWER_BAND,
            BollingerBandsRule.evaluate(fixture, BigDecimal("90.50"), nearPercent = 1.0),
        )
    }

    @Test
    fun `price comfortably within bands`() {
        assertEquals(
            BollingerBandsState.PRICE_WITHIN_BANDS,
            BollingerBandsRule.evaluate(fixture, BigDecimal("100.00"), nearPercent = 1.0),
        )
    }

    @Test
    fun `a breach outside the band wins over a near classification`() {
        assertEquals(
            BollingerBandsState.PRICE_ABOVE_UPPER_BAND,
            BollingerBandsRule.evaluate(fixture, BigDecimal("111.00"), nearPercent = 5.0),
        )
    }
}
