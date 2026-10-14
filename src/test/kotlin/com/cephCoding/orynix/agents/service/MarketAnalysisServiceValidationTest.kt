package com.cephCoding.orynix.agents.service

import com.cephCoding.orynix.agents.domain.ModelTradeSetup
import com.cephCoding.orynix.agents.domain.TradeDirection
import com.cephCoding.orynix.instrument.domain.AssetType
import com.cephCoding.orynix.market.domain.Candle
import com.cephCoding.orynix.market.domain.CandleTimeframe
import com.cephCoding.orynix.marketintelligence.domain.ContextSection
import com.cephCoding.orynix.marketintelligence.domain.ContextSectionStatus
import com.cephCoding.orynix.marketintelligence.domain.MarketDataSummary
import com.cephCoding.orynix.marketintelligence.domain.MarketIntelligenceContext
import com.cephCoding.orynix.technicalanalysis.domain.BollingerBands
import com.cephCoding.orynix.technicalanalysis.domain.Macd
import com.cephCoding.orynix.technicalanalysis.domain.TechnicalAnalysisResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant

class MarketAnalysisServiceValidationTest {

    private fun candle(close: BigDecimal) = Candle(
        symbol = "AAPL", timeframe = CandleTimeframe.DAILY, timestamp = Instant.parse("2026-01-01T00:00:00Z"),
        open = close, high = close, low = close, close = close, volume = BigDecimal("1000000"),
    )

    private fun context(
        latestClose: BigDecimal? = BigDecimal("100"),
        atr14: BigDecimal? = BigDecimal("2"),
    ): MarketIntelligenceContext {
        val marketData = if (latestClose != null) {
            ContextSection(
                ContextSectionStatus.AVAILABLE,
                MarketDataSummary("AAPL", CandleTimeframe.DAILY, candle(latestClose), 365, Instant.now(), Instant.now()),
                null, Instant.now(),
            )
        } else {
            ContextSection<MarketDataSummary>(ContextSectionStatus.NO_DATA, null, "no data", Instant.now())
        }
        val technicalAnalysis = if (atr14 != null) {
            ContextSection(
                ContextSectionStatus.AVAILABLE,
                TechnicalAnalysisResult(
                    symbol = "AAPL", timeframe = CandleTimeframe.DAILY, timestamp = Instant.now(),
                    sma20 = BigDecimal("100"), sma50 = BigDecimal("100"), ema20 = BigDecimal("100"), rsi14 = BigDecimal("50"),
                    macd = Macd(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO),
                    bollingerBands = BollingerBands(BigDecimal("105"), BigDecimal("100"), BigDecimal("95")),
                    atr14 = atr14, volumeSma20 = BigDecimal("1000000"), volumeChangePercent = null,
                ),
                null, Instant.now(),
            )
        } else {
            ContextSection<TechnicalAnalysisResult>(ContextSectionStatus.NO_DATA, null, "insufficient history", Instant.now())
        }
        return MarketIntelligenceContext(
            symbol = "AAPL", assetType = AssetType.STOCK, asOf = Instant.now(),
            marketData = marketData, technicalAnalysis = technicalAnalysis,
            signals = ContextSection(ContextSectionStatus.NO_DATA, null, null, Instant.now()),
            companyInformation = ContextSection(ContextSectionStatus.NO_DATA, null, null, Instant.now()),
            instrumentInformation = ContextSection(ContextSectionStatus.NO_DATA, null, null, Instant.now()),
            news = ContextSection(ContextSectionStatus.NO_DATA, null, null, Instant.now()),
            sentiment = ContextSection(ContextSectionStatus.NO_DATA, null, null, Instant.now()),
        )
    }

    private fun longSetup(entry: String, stop: String, takeProfit: String? = null) = ModelTradeSetup(
        direction = TradeDirection.LONG, entryPrice = BigDecimal(entry), stopLossPrice = BigDecimal(stop),
        takeProfitPrice = takeProfit?.let(::BigDecimal), rationale = "test",
    )

    private fun shortSetup(entry: String, stop: String, takeProfit: String? = null) = ModelTradeSetup(
        direction = TradeDirection.SHORT, entryPrice = BigDecimal(entry), stopLossPrice = BigDecimal(stop),
        takeProfitPrice = takeProfit?.let(::BigDecimal), rationale = "test",
    )

    @Test
    fun `a consistent LONG setup near the known price survives`() {
        val result = MarketAnalysisService.validateAgainstMarket(longSetup("100", "95", "115"), context())
        assertNotNull(result)
    }

    @Test
    fun `a LONG setup with stop at or above entry is dropped`() {
        assertNull(MarketAnalysisService.validateAgainstMarket(longSetup("100", "100"), context()))
        assertNull(MarketAnalysisService.validateAgainstMarket(longSetup("100", "105"), context()))
    }

    @Test
    fun `a LONG setup with take-profit at or below entry is dropped`() {
        assertNull(MarketAnalysisService.validateAgainstMarket(longSetup("100", "95", "100"), context()))
        assertNull(MarketAnalysisService.validateAgainstMarket(longSetup("100", "95", "90"), context()))
    }

    @Test
    fun `a consistent SHORT setup near the known price survives`() {
        val result = MarketAnalysisService.validateAgainstMarket(shortSetup("100", "105", "85"), context())
        assertNotNull(result)
    }

    @Test
    fun `a SHORT setup with stop at or below entry is dropped`() {
        assertNull(MarketAnalysisService.validateAgainstMarket(shortSetup("100", "100"), context()))
        assertNull(MarketAnalysisService.validateAgainstMarket(shortSetup("100", "95"), context()))
    }

    @Test
    fun `an entry price far beyond the ATR-based tolerance is dropped`() {
        // close=100, atr=2 -> tolerance = 2*10 = 20 -> plausible range [80,120]; 500 is far outside it
        val result = MarketAnalysisService.validateAgainstMarket(longSetup("500", "450"), context(latestClose = BigDecimal("100"), atr14 = BigDecimal("2")))
        assertNull(result)
    }

    @Test
    fun `an entry price within the ATR-based tolerance survives`() {
        val result = MarketAnalysisService.validateAgainstMarket(longSetup("110", "95"), context(latestClose = BigDecimal("100"), atr14 = BigDecimal("2")))
        assertNotNull(result)
    }

    @Test
    fun `when ATR is unavailable, a percent-of-close tolerance is used instead and still rejects an absurd level`() {
        val result = MarketAnalysisService.validateAgainstMarket(
            longSetup("1000", "900"),
            context(latestClose = BigDecimal("100"), atr14 = null),
        )
        assertNull(result)
    }

    @Test
    fun `when no market data is available at all, direction consistency alone still governs`() {
        val consistent = MarketAnalysisService.validateAgainstMarket(
            longSetup("100", "95"),
            context(latestClose = null, atr14 = null),
        )
        assertNotNull(consistent)

        val inconsistent = MarketAnalysisService.validateAgainstMarket(
            longSetup("100", "105"),
            context(latestClose = null, atr14 = null),
        )
        assertNull(inconsistent)
    }
}
