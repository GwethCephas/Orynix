package com.cephCoding.orynix.technicalanalysis

import com.cephCoding.orynix.common.exception.MarketDataProviderException
import com.cephCoding.orynix.market.domain.Candle
import com.cephCoding.orynix.market.domain.CandleTimeframe
import com.cephCoding.orynix.support.AbstractIntegrationTest
import com.cephCoding.orynix.technicalanalysis.dto.TechnicalAnalysisResponse
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.get
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.math.sin

class TechnicalAnalysisTest : AbstractIntegrationTest() {

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
    fun `authenticated request with enough history returns a populated analysis`() {
        val auth = register("ta-success@example.com")
        val candles = candleFixture("TAGOOD", 60)
        fakeMarketDataProvider.candlesToReturn = candles

        val response = mockMvc.get("/api/v1/technical-analysis") {
            param("symbol", "TAGOOD")
            param("timeframe", "1d")
            header("Authorization", "Bearer ${auth.accessToken}")
        }.andExpect {
            status { isOk() }
        }.andReturn().response

        val body = objectMapper.readValue(response.contentAsString, TechnicalAnalysisResponse::class.java)
        assert(body.symbol == "TAGOOD")
        assert(body.timeframe == "1d")
        assert(body.timestamp == candles.last().timestamp)
    }

    @Test
    fun `invalid symbol is rejected with 400`() {
        val auth = register("ta-badsymbol@example.com")

        mockMvc.get("/api/v1/technical-analysis") {
            param("symbol", "this-is-not-a-ticker-123")
            param("timeframe", "1d")
            header("Authorization", "Bearer ${auth.accessToken}")
        }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `unsupported timeframe is rejected with 400`() {
        val auth = register("ta-badtimeframe@example.com")

        mockMvc.get("/api/v1/technical-analysis") {
            param("symbol", "TAGOOD")
            param("timeframe", "1h")
            header("Authorization", "Bearer ${auth.accessToken}")
        }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `insufficient historical data is rejected with 422`() {
        val auth = register("ta-insufficient@example.com")
        fakeMarketDataProvider.candlesToReturn = candleFixture("TASMALL", 10)

        val response = mockMvc.get("/api/v1/technical-analysis") {
            param("symbol", "TASMALL")
            param("timeframe", "1d")
            header("Authorization", "Bearer ${auth.accessToken}")
        }.andReturn().response

        assert(response.status == 422)
    }

    @Test
    fun `market data provider failure surfaces as 502 without leaking details`() {
        val auth = register("ta-providerfail@example.com")
        fakeMarketDataProvider.failure = MarketDataProviderException("Market data provider request failed")

        mockMvc.get("/api/v1/technical-analysis") {
            param("symbol", "TAERR")
            param("timeframe", "1d")
            header("Authorization", "Bearer ${auth.accessToken}")
        }.andExpect {
            status { isBadGateway() }
            jsonPath("$.message") { value("Market data provider request failed") }
        }
    }

    @Test
    fun `unauthenticated request is rejected`() {
        mockMvc.get("/api/v1/technical-analysis") {
            param("symbol", "TAGOOD")
            param("timeframe", "1d")
        }.andExpect { status { isUnauthorized() } }
    }
}
