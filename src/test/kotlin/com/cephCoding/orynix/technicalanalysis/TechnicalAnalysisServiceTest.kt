package com.cephCoding.orynix.technicalanalysis

import com.cephCoding.orynix.common.exception.InsufficientCandleDataException
import com.cephCoding.orynix.common.exception.MarketDataProviderException
import com.cephCoding.orynix.market.domain.Candle
import com.cephCoding.orynix.market.domain.CandleTimeframe
import com.cephCoding.orynix.support.AbstractIntegrationTest
import com.cephCoding.orynix.technicalanalysis.indicator.Atr
import com.cephCoding.orynix.technicalanalysis.indicator.BollingerBandsCalculator
import com.cephCoding.orynix.technicalanalysis.indicator.Ema
import com.cephCoding.orynix.technicalanalysis.indicator.MacdCalculator
import com.cephCoding.orynix.technicalanalysis.indicator.Rsi
import com.cephCoding.orynix.technicalanalysis.indicator.Sma
import com.cephCoding.orynix.technicalanalysis.indicator.VolumeIndicators
import com.cephCoding.orynix.technicalanalysis.service.TechnicalAnalysisService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.math.sin

class TechnicalAnalysisServiceTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var technicalAnalysisService: TechnicalAnalysisService

    // A gentle upward trend with oscillation (not monotonic) so RSI/MACD/Bollinger aren't
    // degenerate (an always-increasing fixture would peg RSI at 100 regardless of period,
    // which would hide a wiring bug such as the wrong period being passed through).
    private fun candleFixture(symbol: String, count: Int): List<Candle> {
        val base = Instant.parse("2026-01-01T00:00:00Z")
        return (0 until count).map { i ->
            val close = 100.0 + i * 0.3 + 2.0 * sin(i * 0.5)
            Candle(
                symbol = symbol,
                timeframe = CandleTimeframe.DAILY,
                timestamp = base.plus(i.toLong(), ChronoUnit.DAYS),
                open = BigDecimal.valueOf(close - 0.25),
                high = BigDecimal.valueOf(close + 1.0),
                low = BigDecimal.valueOf(close - 1.0),
                close = BigDecimal.valueOf(close),
                volume = BigDecimal.valueOf(1_000_000.0 + i * 1_000.0),
            )
        }
    }

    @Test
    fun `computes indicators from the candles returned by market data`() {
        val candles = candleFixture("TAOK", 60)
        fakeMarketDataProvider.candlesToReturn = candles

        val result = technicalAnalysisService.getAnalysis("taok", "1d")

        assertEquals("TAOK", result.symbol)
        assertEquals(CandleTimeframe.DAILY, result.timeframe)
        assertEquals(candles.last().timestamp, result.timestamp)

        val closes = candles.map { it.close.toDouble() }
        val highs = candles.map { it.high.toDouble() }
        val lows = candles.map { it.low.toDouble() }
        val volumes = candles.map { it.volume.toDouble() }

        // Tolerances absorb only the BigDecimal rounding applied at the service boundary
        // (scale 4 for price fields, scale 2 for volume fields) — the underlying Double math
        // is already covered by the indicator unit tests, so this is purely a wiring check.
        assertEquals(Sma.calculate(closes, 20).last(), result.sma20.toDouble(), 1e-3)
        assertEquals(Sma.calculate(closes, 50).last(), result.sma50.toDouble(), 1e-3)
        assertEquals(Ema.calculate(closes, 20).last(), result.ema20.toDouble(), 1e-3)
        assertEquals(Rsi.calculate(closes, 14).last(), result.rsi14.toDouble(), 1e-3)
        assertEquals(Atr.calculate(highs, lows, closes, 14).last(), result.atr14.toDouble(), 1e-3)
        assertEquals(VolumeIndicators.sma(volumes, 20).last(), result.volumeSma20.toDouble(), 1e-1)

        val expectedMacd = MacdCalculator.calculate(closes, 12, 26, 9)
        assertEquals(expectedMacd.macdLine.last(), result.macd.macd.toDouble(), 1e-3)
        assertEquals(expectedMacd.signalLine.last(), result.macd.signal.toDouble(), 1e-3)
        assertEquals(expectedMacd.histogram.last(), result.macd.histogram.toDouble(), 1e-3)

        val expectedBollinger = BollingerBandsCalculator.calculate(closes, 20, 2.0)
        assertEquals(expectedBollinger.upper.last(), result.bollingerBands.upper.toDouble(), 1e-3)
        assertEquals(expectedBollinger.middle.last(), result.bollingerBands.middle.toDouble(), 1e-3)
        assertEquals(expectedBollinger.lower.last(), result.bollingerBands.lower.toDouble(), 1e-3)

        // Volumes in this fixture are always positive, so the change percentage is always defined.
        val expectedVolumeChange = VolumeIndicators.changePercent(volumes).last()!!
        assertEquals(expectedVolumeChange, result.volumeChangePercent!!.toDouble(), 1e-1)
    }

    @Test
    fun `fewer candles than required throws insufficient data`() {
        fakeMarketDataProvider.candlesToReturn = candleFixture("TASHORT", 10)

        assertThrows(InsufficientCandleDataException::class.java) {
            technicalAnalysisService.getAnalysis("tashort", "1d")
        }
    }

    @Test
    fun `market data provider failure propagates unchanged`() {
        fakeMarketDataProvider.failure = MarketDataProviderException("Market data provider request failed")

        assertThrows(MarketDataProviderException::class.java) {
            technicalAnalysisService.getAnalysis("TAFAIL", "1d")
        }
    }

    @Test
    fun `getAnalysisHistory's current observation matches getAnalysis on the same data`() {
        fakeMarketDataProvider.candlesToReturn = candleFixture("TAHIST", 60)

        val analysis = technicalAnalysisService.getAnalysis("tahist", "1d")
        val history = technicalAnalysisService.getAnalysisHistory("tahist", "1d")

        assertEquals(analysis, history.current)
    }

    @Test
    fun `the history floor is independent of and one higher than the plain analysis floor`() {
        fakeMarketDataProvider.candlesToReturn = candleFixture("TABOUNDARY", 50)

        technicalAnalysisService.getAnalysis("taboundary", "1d")

        assertThrows(InsufficientCandleDataException::class.java) {
            technicalAnalysisService.getAnalysisHistory("taboundary", "1d")
        }
    }
}
