package com.cephCoding.orynix.signals

import com.cephCoding.orynix.common.exception.MarketDataProviderException
import com.cephCoding.orynix.market.domain.Candle
import com.cephCoding.orynix.market.domain.CandleTimeframe
import com.cephCoding.orynix.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.get
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.math.sin

class SignalAnalysisTest : AbstractIntegrationTest() {

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
    fun `authenticated request with enough history returns a populated signal summary`() {
        val auth = register("signals-success@example.com")
        fakeMarketDataProvider.candlesToReturn = candleFixture("SIGGOOD", 60)

        mockMvc.get("/api/v1/signals") {
            param("symbol", "SIGGOOD")
            param("timeframe", "1d")
            header("Authorization", "Bearer ${auth.accessToken}")
        }.andExpect {
            status { isOk() }
            jsonPath("$.symbol") { value("SIGGOOD") }
            jsonPath("$.timeframe") { value("1d") }
            jsonPath("$.signals.length()") { value(8) }
            jsonPath("$.signals[0].type") { value("TREND") }
        }
    }

    @Test
    fun `invalid symbol is rejected with 400`() {
        val auth = register("signals-badsymbol@example.com")

        mockMvc.get("/api/v1/signals") {
            param("symbol", "this-is-not-a-ticker-123")
            param("timeframe", "1d")
            header("Authorization", "Bearer ${auth.accessToken}")
        }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `unsupported timeframe is rejected with 400`() {
        val auth = register("signals-badtimeframe@example.com")

        mockMvc.get("/api/v1/signals") {
            param("symbol", "SIGGOOD")
            param("timeframe", "1h")
            header("Authorization", "Bearer ${auth.accessToken}")
        }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `insufficient historical data is rejected with 422`() {
        val auth = register("signals-insufficient@example.com")
        fakeMarketDataProvider.candlesToReturn = candleFixture("SIGSMALL", 20)

        val response = mockMvc.get("/api/v1/signals") {
            param("symbol", "SIGSMALL")
            param("timeframe", "1d")
            header("Authorization", "Bearer ${auth.accessToken}")
        }.andReturn().response

        assert(response.status == 422)
    }

    @Test
    fun `market data provider failure surfaces as 502 without leaking details`() {
        val auth = register("signals-providerfail@example.com")
        fakeMarketDataProvider.failure = MarketDataProviderException("Market data provider request failed")

        mockMvc.get("/api/v1/signals") {
            param("symbol", "SIGERR")
            param("timeframe", "1d")
            header("Authorization", "Bearer ${auth.accessToken}")
        }.andExpect {
            status { isBadGateway() }
            jsonPath("$.message") { value("Market data provider request failed") }
        }
    }

    @Test
    fun `unauthenticated request is rejected`() {
        mockMvc.get("/api/v1/signals") {
            param("symbol", "SIGGOOD")
            param("timeframe", "1d")
        }.andExpect { status { isUnauthorized() } }
    }
}
