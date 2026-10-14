package com.cephCoding.orynix.agents

import com.cephCoding.orynix.agents.domain.AnalysisProvenance
import com.cephCoding.orynix.agents.domain.AnalysisSource
import com.cephCoding.orynix.agents.domain.ModelAnalysis
import com.cephCoding.orynix.agents.domain.ModelScenario
import com.cephCoding.orynix.agents.domain.ModelTradeSetup
import com.cephCoding.orynix.agents.domain.ScenarioLabel
import com.cephCoding.orynix.agents.domain.TradeDirection
import com.cephCoding.orynix.agents.dto.MarketAnalysisRequest
import com.cephCoding.orynix.agents.dto.MarketAnalysisResponse
import com.cephCoding.orynix.common.exception.CompanyNotFoundException
import com.cephCoding.orynix.common.exception.MarketAnalysisProviderException
import com.cephCoding.orynix.common.exception.MarketAnalysisResponseException
import com.cephCoding.orynix.fundamentals.domain.CompanyProfile
import com.cephCoding.orynix.market.domain.Candle
import com.cephCoding.orynix.market.domain.CandleTimeframe
import com.cephCoding.orynix.marketintelligence.domain.ContextSectionStatus
import com.cephCoding.orynix.news.domain.NewsArticle
import com.cephCoding.orynix.sentiment.domain.AnalyzedSentiment
import com.cephCoding.orynix.sentiment.domain.Sentiment
import com.cephCoding.orynix.support.AbstractIntegrationTest
import com.cephCoding.orynix.tradingaccount.domain.TradingPlatform
import com.cephCoding.orynix.tradingaccount.dto.TradingAccountProfileRequest
import com.cephCoding.orynix.tradingaccount.dto.TradingAccountProfileResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.post
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit

class MarketAnalysisControllerTest : AbstractIntegrationTest() {

    private fun candle(symbol: String, day: Int): Candle {
        val close = BigDecimal(100 + (day % 5))
        return Candle(
            symbol = symbol, timeframe = CandleTimeframe.DAILY,
            timestamp = Instant.parse("2026-01-01T00:00:00Z").plus(day.toLong(), ChronoUnit.DAYS),
            open = close, high = close.add(BigDecimal("1")), low = close.subtract(BigDecimal("1")), close = close,
            volume = BigDecimal("1000000"),
        )
    }

    private fun sufficientCandles(symbol: String) = (0 until 60).map { candle(symbol, it) }

    /**
     * MarketIntelligenceService.getContext always calls the fundamentals provider for a STOCK
     * instrument - FakeFundamentalDataProvider throws IllegalStateException if its
     * companyProfileToReturn was never set, which is not one of the exceptions
     * MarketIntelligenceService's runSection downgrades to NO_DATA/ERROR, so it would otherwise
     * propagate as an unrelated 500 in every stock-symbol test below. Tests that specifically want
     * to exercise a missing-fundamentals case set `.failure` themselves instead of calling this.
     */
    private fun setupStockFixtures(symbol: String) {
        fakeMarketDataProvider.candlesToReturn = sufficientCandles(symbol)
        fakeFundamentalDataProvider.companyProfileToReturn = CompanyProfile("Apple Inc.", "NASDAQ", "Technology", BigDecimal("3000000000000"))
    }

    private fun analyze(token: String, request: MarketAnalysisRequest) = mockMvc.post("/api/v1/market-analyses") {
        contentType = MediaType.APPLICATION_JSON
        content = objectMapper.writeValueAsString(request)
        header("Authorization", "Bearer $token")
    }

    private fun createProfile(token: String, balance: BigDecimal = BigDecimal("10000")): TradingAccountProfileResponse {
        val request = TradingAccountProfileRequest(
            name = "AI analysis profile", platform = TradingPlatform.OTHER, platformDetails = null,
            accountCurrency = "USD", balance = balance, riskPercentPerTrade = BigDecimal("1"),
            maxDailyLossPercent = null, maxConcurrentPositions = null,
        )
        val response = mockMvc.post("/api/v1/trading-accounts") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
            header("Authorization", "Bearer $token")
        }.andReturn().response
        assertEquals(201, response.status)
        return objectMapper.readValue(response.contentAsString, TradingAccountProfileResponse::class.java)
    }

    @Test
    fun `a blank symbol is rejected with 400`() {
        val auth = register("ai-blanksymbol@example.com")
        analyze(auth.accessToken, MarketAnalysisRequest(symbol = "")).andExpect { status { isBadRequest() } }
    }

    @Test
    fun `an unresolvable symbol is rejected with 400`() {
        val auth = register("ai-badsymbol@example.com")
        analyze(auth.accessToken, MarketAnalysisRequest(symbol = "###")).andExpect { status { isBadRequest() } }
    }

    @Test
    fun `a stock symbol with available news and sentiment produces a full happy-path response`() {
        val auth = register("ai-stockhappy@example.com")
        setupStockFixtures("AAPL")
        fakeNewsProvider.articlesToReturn = listOf(
            NewsArticle(null, "p1", "AAPL", "Apple announces new product", null, "Reuters", null, null, Instant.parse("2026-01-01T00:00:00Z")),
        )
        fakeSentimentAnalyzer.sentimentToReturn = AnalyzedSentiment(Sentiment.POSITIVE, BigDecimal("0.5"), BigDecimal("0.8"))
        fakeMarketAnalysisProvider.nextResponse = ModelAnalysis(
            provenance = AnalysisProvenance(AnalysisSource.MODEL, "gemini", "gemini-2.0-flash"),
            marketAssessment = "Shares are consolidating near recent highs.",
            scenarios = listOf(ModelScenario(ScenarioLabel.BULLISH, "Breakout likely.", null)),
            tradeSetups = emptyList(),
        )

        val response = analyze(auth.accessToken, MarketAnalysisRequest(symbol = "AAPL")).andReturn().response
        assertEquals(200, response.status)
        val body = objectMapper.readValue(response.contentAsString, MarketAnalysisResponse::class.java)

        assertEquals("AAPL", body.symbol)
        assertEquals(AnalysisSource.MODEL, body.provenance.source)
        assertEquals("Shares are consolidating near recent highs.", body.marketAssessment)
        assertEquals(1, body.scenarios.size)
        assertEquals(ContextSectionStatus.AVAILABLE, body.evidenceStatus.news.status)
        assertEquals(ContextSectionStatus.AVAILABLE, body.evidenceStatus.sentiment.status)
        assertEquals(ContextSectionStatus.AVAILABLE, body.evidenceStatus.marketData.status)
    }

    @Test
    fun `a forex symbol reports news and sentiment as unavailable without erroring`() {
        val auth = register("ai-forex@example.com")
        fakeMarketDataProvider.candlesToReturn = sufficientCandles("EUR/USD")
        fakeMarketAnalysisProvider.nextResponse = ModelAnalysis(
            provenance = AnalysisProvenance(AnalysisSource.MODEL, "gemini", "gemini-2.0-flash"),
            marketAssessment = "EUR/USD is range-bound.",
            scenarios = emptyList(),
            tradeSetups = emptyList(),
        )

        val response = analyze(auth.accessToken, MarketAnalysisRequest(symbol = "EUR/USD")).andReturn().response
        assertEquals(200, response.status)
        val body = objectMapper.readValue(response.contentAsString, MarketAnalysisResponse::class.java)

        assertEquals(ContextSectionStatus.NO_DATA, body.evidenceStatus.news.status)
        assertEquals(ContextSectionStatus.NO_DATA, body.evidenceStatus.sentiment.status)
        assertEquals(ContextSectionStatus.AVAILABLE, body.evidenceStatus.marketData.status)
    }

    @Test
    fun `one unavailable data source does not fail the whole request`() {
        val auth = register("ai-partial@example.com")
        setupStockFixtures("AAPL")
        fakeFundamentalDataProvider.failure = CompanyNotFoundException("AAPL")
        fakeMarketAnalysisProvider.nextResponse = ModelAnalysis(
            provenance = AnalysisProvenance(AnalysisSource.MODEL, "gemini", "gemini-2.0-flash"),
            marketAssessment = "Assessment without company data.",
            scenarios = emptyList(),
            tradeSetups = emptyList(),
        )

        val response = analyze(auth.accessToken, MarketAnalysisRequest(symbol = "AAPL")).andReturn().response
        assertEquals(200, response.status)
        val body = objectMapper.readValue(response.contentAsString, MarketAnalysisResponse::class.java)

        assertEquals(ContextSectionStatus.NO_DATA, body.evidenceStatus.companyInformation.status)
        assertEquals(ContextSectionStatus.AVAILABLE, body.evidenceStatus.marketData.status)
    }

    @Test
    fun `referencing another user's trading account profile returns 404`() {
        val authA = register("ai-ownera@example.com")
        val authB = register("ai-ownerb@example.com")
        val profile = createProfile(authA.accessToken)
        setupStockFixtures("AAPL")

        analyze(authB.accessToken, MarketAnalysisRequest(symbol = "AAPL", accountProfileId = profile.id))
            .andExpect { status { isNotFound() } }
    }

    @Test
    fun `a provider failure is reported as 502`() {
        val auth = register("ai-providerfail@example.com")
        setupStockFixtures("AAPL")
        fakeMarketAnalysisProvider.failure = MarketAnalysisProviderException()

        analyze(auth.accessToken, MarketAnalysisRequest(symbol = "AAPL")).andExpect { status { isBadGateway() } }
    }

    @Test
    fun `a malformed model response is reported as 502`() {
        val auth = register("ai-malformed@example.com")
        setupStockFixtures("AAPL")
        fakeMarketAnalysisProvider.failure = MarketAnalysisResponseException()

        analyze(auth.accessToken, MarketAnalysisRequest(symbol = "AAPL")).andExpect { status { isBadGateway() } }
    }

    @Test
    fun `a SHORT setup never gets a fabricated size, and states why`() {
        val auth = register("ai-shortsetup@example.com")
        setupStockFixtures("AAPL")
        fakeMarketAnalysisProvider.nextResponse = ModelAnalysis(
            provenance = AnalysisProvenance(AnalysisSource.MODEL, "gemini", "gemini-2.0-flash"),
            marketAssessment = "Bearish setup.",
            scenarios = emptyList(),
            tradeSetups = listOf(
                ModelTradeSetup(TradeDirection.SHORT, BigDecimal("100"), BigDecimal("105"), BigDecimal("85"), "Breakdown expected."),
            ),
        )

        val response = analyze(auth.accessToken, MarketAnalysisRequest(symbol = "AAPL", equity = BigDecimal("10000"), accountCurrency = "USD", riskPercentPerTrade = BigDecimal("1")))
            .andReturn().response
        assertEquals(200, response.status)
        val body = objectMapper.readValue(response.contentAsString, MarketAnalysisResponse::class.java)

        assertEquals(1, body.tradeSetups.size)
        assertNull(body.tradeSetups[0].sizing)
        assertTrue(body.tradeSetups[0].limitations.isNotEmpty())
    }

    @Test
    fun `a LONG setup with no account context never gets a fabricated size, and states why`() {
        val auth = register("ai-nocontext@example.com")
        setupStockFixtures("AAPL")
        fakeMarketAnalysisProvider.nextResponse = ModelAnalysis(
            provenance = AnalysisProvenance(AnalysisSource.MODEL, "gemini", "gemini-2.0-flash"),
            marketAssessment = "Bullish setup.",
            scenarios = emptyList(),
            tradeSetups = listOf(
                ModelTradeSetup(TradeDirection.LONG, BigDecimal("100"), BigDecimal("95"), BigDecimal("115"), "Breakout expected."),
            ),
        )

        val response = analyze(auth.accessToken, MarketAnalysisRequest(symbol = "AAPL")).andReturn().response
        assertEquals(200, response.status)
        val body = objectMapper.readValue(response.contentAsString, MarketAnalysisResponse::class.java)

        assertNull(body.tradeSetups[0].sizing)
        assertTrue(body.tradeSetups[0].limitations.isNotEmpty())
    }

    @Test
    fun `a LONG setup with account context gets sizing matching a direct risk calculation`() {
        val auth = register("ai-sizedlong@example.com")
        setupStockFixtures("AAPL")
        fakeMarketAnalysisProvider.nextResponse = ModelAnalysis(
            provenance = AnalysisProvenance(AnalysisSource.MODEL, "gemini", "gemini-2.0-flash"),
            marketAssessment = "Bullish setup.",
            scenarios = emptyList(),
            tradeSetups = listOf(
                ModelTradeSetup(TradeDirection.LONG, BigDecimal("100"), BigDecimal("98"), BigDecimal("106"), "Breakout expected."),
            ),
        )

        val response = analyze(
            auth.accessToken,
            MarketAnalysisRequest(symbol = "AAPL", equity = BigDecimal("10000"), accountCurrency = "USD", riskPercentPerTrade = BigDecimal("1")),
        ).andReturn().response
        assertEquals(200, response.status)
        val body = objectMapper.readValue(response.contentAsString, MarketAnalysisResponse::class.java)

        val sizing = body.tradeSetups[0].sizing
        assertNotNull(sizing)
        // riskBudget = 10000 * 1% = 100; riskPerUnit = 100-98 = 2 -> quantity = 50
        assertEquals(0, BigDecimal("50").compareTo(sizing!!.quantity))
        assertEquals(0, BigDecimal("100").compareTo(sizing.estimatedLossAtStop))
    }

    @Test
    fun `an implausible proposed price level is dropped, not sized or shown`() {
        val auth = register("ai-implausible@example.com")
        setupStockFixtures("AAPL")
        fakeMarketAnalysisProvider.nextResponse = ModelAnalysis(
            provenance = AnalysisProvenance(AnalysisSource.MODEL, "gemini", "gemini-2.0-flash"),
            marketAssessment = "x",
            scenarios = emptyList(),
            tradeSetups = listOf(
                // close is ~100-104; 5000 is wildly implausible
                ModelTradeSetup(TradeDirection.LONG, BigDecimal("5000"), BigDecimal("4900"), null, "implausible"),
            ),
        )

        val response = analyze(auth.accessToken, MarketAnalysisRequest(symbol = "AAPL")).andReturn().response
        assertEquals(200, response.status)
        val body = objectMapper.readValue(response.contentAsString, MarketAnalysisResponse::class.java)

        assertTrue(body.tradeSetups.isEmpty())
    }

    @Test
    fun `missing authentication is rejected with 401`() {
        mockMvc.post("/api/v1/market-analyses") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(MarketAnalysisRequest(symbol = "AAPL"))
        }.andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `scenarios and an empty trade setup list are both valid - nothing is forced`() {
        val auth = register("ai-emptysetups@example.com")
        setupStockFixtures("AAPL")
        fakeMarketAnalysisProvider.nextResponse = ModelAnalysis(
            provenance = AnalysisProvenance(AnalysisSource.MODEL, "gemini", "gemini-2.0-flash"),
            marketAssessment = "No actionable setup today.",
            scenarios = listOf(
                ModelScenario(ScenarioLabel.NEUTRAL, "Range-bound.", null),
            ),
            tradeSetups = emptyList(),
        )

        val response = analyze(auth.accessToken, MarketAnalysisRequest(symbol = "AAPL")).andReturn().response
        assertEquals(200, response.status)
        val body = objectMapper.readValue(response.contentAsString, MarketAnalysisResponse::class.java)

        assertEquals(1, body.scenarios.size)
        assertTrue(body.tradeSetups.isEmpty())
    }
}
