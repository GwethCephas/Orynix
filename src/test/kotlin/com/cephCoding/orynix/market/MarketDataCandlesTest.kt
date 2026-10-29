package com.cephCoding.orynix.market

import com.cephCoding.orynix.common.exception.MarketDataProviderException
import com.cephCoding.orynix.market.domain.Candle
import com.cephCoding.orynix.market.domain.CandleTimeframe
import com.cephCoding.orynix.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.get
import java.math.BigDecimal
import java.time.Instant

class MarketDataCandlesTest : AbstractIntegrationTest() {

    @Test
    fun `authenticated request returns normalized candles`() {
        val auth = register("candles-success@example.com")
        fakeMarketDataProvider.candlesToReturn = listOf(
            Candle(
                symbol = "MSFT",
                timeframe = CandleTimeframe.DAILY,
                timestamp = Instant.parse("2026-01-02T00:00:00Z"),
                open = BigDecimal("300.00"),
                high = BigDecimal("305.00"),
                low = BigDecimal("299.00"),
                close = BigDecimal("301.00"),
                volume = BigDecimal("2000"),
            )
        )

        mockMvc.get("/api/v1/market-data/candles") {
            param("symbol", "MSFT")
            param("timeframe", "1d")
            header("Authorization", "Bearer ${auth.accessToken}")
        }.andExpect {
            status { isOk() }
            jsonPath("$[0].symbol") { value("MSFT") }
            jsonPath("$[0].timeframe") { value("1d") }
        }
    }

    @Test
    fun `invalid timeframe is rejected with 400`() {
        val auth = register("candles-badtimeframe@example.com")

        mockMvc.get("/api/v1/market-data/candles") {
            param("symbol", "MSFT")
            param("timeframe", "bogus")
            header("Authorization", "Bearer ${auth.accessToken}")
        }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `invalid symbol is rejected with 400`() {
        val auth = register("candles-badsymbol@example.com")

        mockMvc.get("/api/v1/market-data/candles") {
            param("symbol", "this-is-not-a-ticker-123")
            param("timeframe", "1d")
            header("Authorization", "Bearer ${auth.accessToken}")
        }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `unauthenticated request is rejected`() {
        mockMvc.get("/api/v1/market-data/candles") {
            param("symbol", "MSFT")
            param("timeframe", "1d")
        }.andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `provider failure surfaces as 502 without leaking details`() {
        val auth = register("candles-providerfail@example.com")
        fakeMarketDataProvider.failure = MarketDataProviderException("Market data provider request failed")

        mockMvc.get("/api/v1/market-data/candles") {
            param("symbol", "MSFT")
            param("timeframe", "1d")
            header("Authorization", "Bearer ${auth.accessToken}")
        }.andExpect {
            status { isBadGateway() }
            jsonPath("$.message") { value("Market data provider request failed") }
        }
    }

    @Test
    fun `supplying from and to routes to the ranged intraday-capable path`() {
        val auth = register("candles-intraday@example.com")
        fakeMarketDataProvider.candlesToReturn = listOf(
            Candle(
                symbol = "MSFT",
                timeframe = CandleTimeframe.MINUTE_15,
                timestamp = Instant.parse("2026-01-02T14:30:00Z"),
                open = BigDecimal("300.00"),
                high = BigDecimal("305.00"),
                low = BigDecimal("299.00"),
                close = BigDecimal("301.00"),
                volume = BigDecimal("2000"),
            )
        )

        mockMvc.get("/api/v1/market-data/candles") {
            param("symbol", "MSFT")
            param("timeframe", "15m")
            param("from", "2026-01-01")
            param("to", "2026-01-02")
            header("Authorization", "Bearer ${auth.accessToken}")
        }.andExpect {
            status { isOk() }
            jsonPath("$[0].symbol") { value("MSFT") }
            jsonPath("$[0].timeframe") { value("15m") }
        }
    }

    @Test
    fun `an intraday request spanning more than its per-interval cap is rejected with 400`() {
        val auth = register("candles-intraday-toowide@example.com")

        mockMvc.get("/api/v1/market-data/candles") {
            param("symbol", "MSFT")
            param("timeframe", "15m")
            param("from", "2026-01-01")
            param("to", "2026-02-01")
            header("Authorization", "Bearer ${auth.accessToken}")
        }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `an intraday timeframe without from and to is rejected rather than silently fetching a year`() {
        val auth = register("candles-intraday-noRange@example.com")

        mockMvc.get("/api/v1/market-data/candles") {
            param("symbol", "MSFT")
            param("timeframe", "15m")
            header("Authorization", "Bearer ${auth.accessToken}")
        }.andExpect { status { isBadRequest() } }
    }
}
