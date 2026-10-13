package com.cephCoding.orynix.marketintelligence

import com.cephCoding.orynix.common.exception.FundamentalDataProviderException
import com.cephCoding.orynix.fundamentals.domain.CompanyProfile
import com.cephCoding.orynix.market.domain.Candle
import com.cephCoding.orynix.market.domain.CandleTimeframe
import com.cephCoding.orynix.support.AbstractIntegrationTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.get
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.math.sin

class MarketIntelligenceControllerTest : AbstractIntegrationTest() {

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
    fun `authenticated request returns a fully assembled context`() {
        val auth = register("market-intelligence-success@example.com")
        fakeMarketDataProvider.candlesToReturn = candleFixture("AAPL", 60)
        fakeFundamentalDataProvider.companyProfileToReturn = CompanyProfile(
            name = "Apple Inc.",
            exchange = "XNAS",
            industry = "ELECTRONIC COMPUTERS",
            marketCapitalization = BigDecimal("4858256580200"),
        )

        mockMvc.get("/api/v1/market-intelligence") {
            param("symbol", "AAPL")
            header("Authorization", "Bearer ${auth.accessToken}")
        }.andExpect {
            status { isOk() }
            jsonPath("$.symbol") { value("AAPL") }
            jsonPath("$.marketData.status") { value("AVAILABLE") }
            jsonPath("$.technicalAnalysis.status") { value("AVAILABLE") }
            jsonPath("$.signals.status") { value("AVAILABLE") }
            jsonPath("$.companyInformation.status") { value("AVAILABLE") }
            jsonPath("$.companyInformation.data.company.name") { value("Apple Inc.") }
            jsonPath("$.news.status") { exists() }
            jsonPath("$.sentiment.status") { exists() }
        }
    }

    @Test
    fun `invalid symbol is rejected with 400`() {
        val auth = register("market-intelligence-badsymbol@example.com")

        mockMvc.get("/api/v1/market-intelligence") {
            param("symbol", "this-is-not-a-ticker-123")
            header("Authorization", "Bearer ${auth.accessToken}")
        }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `a forex pair is accepted end-to-end through the controller with a slash in the symbol`() {
        val auth = register("market-intelligence-forex@example.com")
        fakeMarketDataProvider.candlesToReturn = candleFixture("EUR/USD", 60)

        mockMvc.get("/api/v1/market-intelligence") {
            param("symbol", "EUR/USD")
            header("Authorization", "Bearer ${auth.accessToken}")
        }.andExpect {
            status { isOk() }
            jsonPath("$.symbol") { value("EUR/USD") }
            jsonPath("$.assetType") { value("FOREX") }
            jsonPath("$.marketData.status") { value("AVAILABLE") }
            jsonPath("$.companyInformation.status") { value("NO_DATA") }
            jsonPath("$.instrumentInformation.status") { value("AVAILABLE") }
            jsonPath("$.instrumentInformation.data.baseCurrency") { value("EUR") }
            jsonPath("$.news.status") { value("NO_DATA") }
            jsonPath("$.sentiment.status") { value("NO_DATA") }
        }
    }

    @Test
    fun `unauthenticated request is rejected`() {
        mockMvc.get("/api/v1/market-intelligence") {
            param("symbol", "AAPL")
        }.andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `a provider failure degrades its section but never fails the whole request`() {
        val auth = register("market-intelligence-providerfail@example.com")
        fakeMarketDataProvider.candlesToReturn = candleFixture("AAPL", 60)
        fakeFundamentalDataProvider.failure =
            FundamentalDataProviderException("upstream said: secret-internal-detail-98765")

        val response = mockMvc.get("/api/v1/market-intelligence") {
            param("symbol", "AAPL")
            header("Authorization", "Bearer ${auth.accessToken}")
        }.andExpect {
            status { isOk() }
            jsonPath("$.companyInformation.status") { value("ERROR") }
        }.andReturn().response

        assertFalse(response.contentAsString.contains("secret-internal-detail-98765"))
    }
}
