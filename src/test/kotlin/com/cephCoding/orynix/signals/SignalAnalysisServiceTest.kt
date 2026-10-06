package com.cephCoding.orynix.signals

import com.cephCoding.orynix.common.exception.InsufficientCandleDataException
import com.cephCoding.orynix.common.exception.MarketDataProviderException
import com.cephCoding.orynix.market.domain.Candle
import com.cephCoding.orynix.market.domain.CandleTimeframe
import com.cephCoding.orynix.signals.service.SignalAnalysisService
import com.cephCoding.orynix.support.AbstractIntegrationTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.math.sin

class SignalAnalysisServiceTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var signalAnalysisService: SignalAnalysisService

    private fun candleFixture(
        symbol: String,
        count: Int,
        closeAt: (Int) -> Double = { i -> 100.0 + i * 0.3 + 2.0 * sin(i * 0.5) },
    ): List<Candle> {
        val base = Instant.parse("2026-01-01T00:00:00Z")
        return (0 until count).map { i ->
            val close = closeAt(i)
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
    fun `produces all eight signals in a fixed order with counts summing to the total`() {
        fakeMarketDataProvider.candlesToReturn = candleFixture("SIGOK", 60)

        val summary = signalAnalysisService.getSignals("sigok", "1d")

        assertEquals(
            listOf("TREND", "CROSSOVER", "RSI", "MACD", "BOLLINGER_BANDS", "VOLATILITY", "VOLUME", "PRICE_MOMENTUM"),
            summary.signals.map { it.type.name },
        )
        assertEquals(8, summary.signals.size)
        assertEquals(
            summary.signals.size,
            summary.bullishSignalCount + summary.bearishSignalCount + summary.neutralSignalCount,
        )
    }

    @Test
    fun `a bullish crossover does not inflate the bullish count beyond the independently-directional signals`() {
        // 50 flat candles followed by a sharp final rise: EMA20 (fast) crosses above SMA50 (slow,
        // barely moved by one new point) between the previous and current observation, while the
        // steady-state EMA20-SMA20-SMA50 ordering is also bullish at the latest point — engineered
        // to fire both TREND=BULLISH and CROSSOVER=BULLISH_CROSSOVER from the same relationship.
        val closes = List(50) { 100.0 } + listOf(130.0)
        fakeMarketDataProvider.candlesToReturn = candleFixture("SIGXOVER", 51) { i -> closes[i] }

        val summary = signalAnalysisService.getSignals("sigxover", "1d")

        val trend = summary.signals.first { it.type.name == "TREND" }
        val crossover = summary.signals.first { it.type.name == "CROSSOVER" }
        val macd = summary.signals.first { it.type.name == "MACD" }
        val priceMomentum = summary.signals.first { it.type.name == "PRICE_MOMENTUM" }

        assertEquals("BULLISH", trend.state)
        assertEquals("BULLISH_CROSSOVER", crossover.state)

        // bullishSignalCount must equal exactly what TREND/MACD/PRICE_MOMENTUM contribute on their
        // own — CROSSOVER being bullish too must not add anything beyond that.
        val bullishStates = setOf("BULLISH", "BULLISH_CROSSOVER", "BULLISH_MOMENTUM", "POSITIVE")
        val expectedBullish = listOf(trend, macd, priceMomentum).count { it.state in bullishStates }
        assertEquals(expectedBullish, summary.bullishSignalCount)
    }

    @Test
    fun `fewer than the history floor throws insufficient data`() {
        fakeMarketDataProvider.candlesToReturn = candleFixture("SIGSHORT", 50)

        assertThrows(InsufficientCandleDataException::class.java) {
            signalAnalysisService.getSignals("sigshort", "1d")
        }
    }

    @Test
    fun `market data provider failure propagates unchanged`() {
        fakeMarketDataProvider.failure = MarketDataProviderException("Market data provider request failed")

        assertThrows(MarketDataProviderException::class.java) {
            signalAnalysisService.getSignals("SIGFAIL", "1d")
        }
    }
}
