package com.cephCoding.orynix.backtesting

import com.cephCoding.orynix.backtesting.domain.StrategyType
import com.cephCoding.orynix.backtesting.dto.BacktestDetailResponse
import com.cephCoding.orynix.backtesting.dto.BacktestRequest
import com.cephCoding.orynix.backtesting.dto.BacktestResponse
import com.cephCoding.orynix.backtesting.dto.BacktestSummaryResponse
import com.cephCoding.orynix.common.exception.MarketDataProviderException
import com.cephCoding.orynix.market.domain.Candle
import com.cephCoding.orynix.market.domain.CandleTimeframe
import com.cephCoding.orynix.support.AbstractIntegrationTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

class BacktestControllerTest : AbstractIntegrationTest() {

    private fun candle(symbol: String, day: Int, open: Double, close: Double): Candle = Candle(
        symbol = symbol,
        timeframe = CandleTimeframe.DAILY,
        timestamp = Instant.parse("2026-01-01T00:00:00Z").plus(day.toLong(), ChronoUnit.DAYS),
        open = BigDecimal.valueOf(open),
        high = BigDecimal.valueOf(maxOf(open, close) + 1.0),
        low = BigDecimal.valueOf(minOf(open, close) - 1.0),
        close = BigDecimal.valueOf(close),
        volume = BigDecimal.valueOf(1_000_000.0),
    )

    // Hand-computed bullish-then-bearish SMA(2)/SMA(3) crossover, identical to the one verified
    // in MovingAverageCrossoverStrategyTest: entry decided at index 3, exit decided at index 4,
    // filled at index 5's open. Padded with two trailing flat bars so index 4 is not the final
    // candle, letting the SIGNAL exit (not an END_OF_PERIOD force-close) resolve the trade.
    private fun crossoverCandles(symbol: String): List<Candle> {
        val closes = listOf(100.0, 100.0, 100.0, 130.0, 60.0, 90.0, 90.0)
        return closes.mapIndexed { i, close -> candle(symbol, i, open = close - 1.0, close = close) }
    }

    private fun flatCandles(symbol: String, count: Int): List<Candle> =
        (0 until count).map { candle(symbol, it, open = 100.0, close = 100.0) }

    private fun movingAverageCrossoverRequest(symbol: String = "BTEST") = BacktestRequest(
        symbol = symbol,
        fromDate = LocalDate.of(2026, 1, 4),
        toDate = LocalDate.of(2026, 1, 10),
        strategyType = StrategyType.MOVING_AVERAGE_CROSSOVER,
        fastPeriod = 2,
        slowPeriod = 3,
        rsiPeriod = null,
        oversoldThreshold = null,
        overboughtThreshold = null,
        initialCapital = BigDecimal("1000"),
        accountCurrency = "USD",
        quantity = BigDecimal("1"),
        commissionPerOrder = BigDecimal("0"),
        slippageBps = 0,
    )

    private fun postBacktest(token: String, request: BacktestRequest) = mockMvc.post("/api/v1/backtests") {
        contentType = MediaType.APPLICATION_JSON
        content = objectMapper.writeValueAsString(request)
        header("Authorization", "Bearer $token")
    }

    @Test
    fun `a known crossover scenario produces the hand-computed trade and metrics`() {
        val auth = register("backtest-crossover@example.com")
        fakeMarketDataProvider.candlesToReturn = crossoverCandles("BTEST")

        val response = postBacktest(auth.accessToken, movingAverageCrossoverRequest()).andReturn().response
        assertEquals(201, response.status)
        val body = objectMapper.readValue(response.contentAsString, BacktestResponse::class.java)

        assertEquals("BTEST", body.symbol)
        assertEquals(1, body.totalTrades)
        assertEquals(1, body.winningTrades)
        assertEquals(0, body.losingTrades)
        assertEquals(0, body.breakevenTrades)
        assertEquals(BigDecimal("30.0000"), body.netPnl)
        assertEquals(BigDecimal("3.0000"), body.netReturnPercent)
        assertEquals(BigDecimal("1030.0000"), body.finalEquity)
        assertEquals(BigDecimal("0.0000"), body.maxDrawdownPercent)
        assertEquals(BigDecimal("100.0000"), body.winRate)
        assertEquals(null, body.profitFactor)
    }

    @Test
    fun `a valid forex request is accepted end-to-end`() {
        val auth = register("backtest-forex@example.com")
        fakeMarketDataProvider.candlesToReturn = flatCandles("EUR/USD", 10)

        val request = movingAverageCrossoverRequest(symbol = "EUR/USD").copy(accountCurrency = "USD")
        val response = postBacktest(auth.accessToken, request).andReturn().response
        assertEquals(201, response.status)
        val body = objectMapper.readValue(response.contentAsString, BacktestResponse::class.java)

        assertEquals("EUR/USD", body.symbol)
        assertEquals(0, body.totalTrades)
        assertEquals(BigDecimal("1000.0000"), body.finalEquity)
    }

    @Test
    fun `a date range exceeding the maximum is rejected with 400`() {
        val auth = register("backtest-range@example.com")

        val request = movingAverageCrossoverRequest().copy(fromDate = LocalDate.of(2020, 1, 1), toDate = LocalDate.of(2026, 1, 1))
        postBacktest(auth.accessToken, request).andExpect { status { isBadRequest() } }
    }

    @Test
    fun `cross-strategy parameters are rejected with 400 instead of silently ignored`() {
        val auth = register("backtest-crossparams@example.com")

        val request = movingAverageCrossoverRequest().copy(rsiPeriod = 14)
        postBacktest(auth.accessToken, request).andExpect { status { isBadRequest() } }
    }

    @Test
    fun `a forex account currency mismatch is rejected with 400`() {
        val auth = register("backtest-forexcurrency@example.com")

        val request = movingAverageCrossoverRequest(symbol = "EUR/USD").copy(accountCurrency = "EUR")
        postBacktest(auth.accessToken, request).andExpect { status { isBadRequest() } }
    }

    @Test
    fun `a stock account currency other than USD is rejected with 400`() {
        val auth = register("backtest-stockcurrency@example.com")

        val request = movingAverageCrossoverRequest().copy(accountCurrency = "EUR")
        postBacktest(auth.accessToken, request).andExpect { status { isBadRequest() } }
    }

    @Test
    fun `too few candles for the requested warmup is rejected with 422`() {
        val auth = register("backtest-insufficient@example.com")
        fakeMarketDataProvider.candlesToReturn = flatCandles("BTEST", 2)

        val request = movingAverageCrossoverRequest().copy(fromDate = LocalDate.of(2026, 1, 1))
        val response = postBacktest(auth.accessToken, request).andReturn().response
        assertEquals(422, response.status)
    }

    @Test
    fun `a market data provider failure is surfaced as 502`() {
        val auth = register("backtest-providerfailure@example.com")
        fakeMarketDataProvider.failure = MarketDataProviderException("Market data provider request failed")

        postBacktest(auth.accessToken, movingAverageCrossoverRequest()).andExpect { status { isBadGateway() } }
    }

    @Test
    fun `a malformed symbol is rejected with 400 and nothing is persisted`() {
        val auth = register("backtest-badsymbol@example.com")

        val request = movingAverageCrossoverRequest(symbol = "###")
        val response = postBacktest(auth.accessToken, request).andReturn().response
        assertEquals(400, response.status)
        assertTrue(response.contentAsString.contains("Invalid symbol"))

        val listResponse = mockMvc.get("/api/v1/backtests") { header("Authorization", "Bearer ${auth.accessToken}") }
            .andReturn().response
        val runs = objectMapper.readValue(listResponse.contentAsString, Array<BacktestSummaryResponse>::class.java)
        assertEquals(0, runs.size)
    }

    @Test
    fun `a user cannot read another user backtest, and a missing id looks identical`() {
        val ownerAuth = register("backtest-owner@example.com")
        val otherAuth = register("backtest-other@example.com")
        fakeMarketDataProvider.candlesToReturn = crossoverCandles("BTEST")

        val createResponse = postBacktest(ownerAuth.accessToken, movingAverageCrossoverRequest()).andReturn().response
        val created = objectMapper.readValue(createResponse.contentAsString, BacktestResponse::class.java)

        mockMvc.get("/api/v1/backtests/${created.id}") { header("Authorization", "Bearer ${otherAuth.accessToken}") }
            .andExpect { status { isNotFound() } }

        mockMvc.get("/api/v1/backtests/${UUID.randomUUID()}") { header("Authorization", "Bearer ${ownerAuth.accessToken}") }
            .andExpect { status { isNotFound() } }
    }

    @Test
    fun `listing backtests returns only the authenticated user own runs`() {
        val userA = register("backtest-lista@example.com")
        val userB = register("backtest-listb@example.com")
        fakeMarketDataProvider.candlesToReturn = flatCandles("BTEST", 10)

        assertEquals(201, postBacktest(userA.accessToken, movingAverageCrossoverRequest()).andReturn().response.status)
        assertEquals(201, postBacktest(userA.accessToken, movingAverageCrossoverRequest()).andReturn().response.status)
        assertEquals(201, postBacktest(userB.accessToken, movingAverageCrossoverRequest()).andReturn().response.status)

        val listResponse = mockMvc.get("/api/v1/backtests") { header("Authorization", "Bearer ${userA.accessToken}") }
            .andReturn().response
        val runs = objectMapper.readValue(listResponse.contentAsString, Array<BacktestSummaryResponse>::class.java)

        assertEquals(2, runs.size)
    }

    @Test
    fun `the trades endpoint returns the trade and equity curve, scoped by ownership`() {
        val ownerAuth = register("backtest-tradesowner@example.com")
        val otherAuth = register("backtest-tradesother@example.com")
        fakeMarketDataProvider.candlesToReturn = crossoverCandles("BTEST")

        val createResponse = postBacktest(ownerAuth.accessToken, movingAverageCrossoverRequest()).andReturn().response
        val created = objectMapper.readValue(createResponse.contentAsString, BacktestResponse::class.java)

        val detailResponse = mockMvc.get("/api/v1/backtests/${created.id}/trades") {
            header("Authorization", "Bearer ${ownerAuth.accessToken}")
        }.andReturn().response
        val detail = objectMapper.readValue(detailResponse.contentAsString, BacktestDetailResponse::class.java)

        assertEquals(1, detail.trades.size)
        assertTrue(detail.equityCurve.isNotEmpty())
        assertEquals(detail.equityCurve.sortedBy { it.sequenceNumber }, detail.equityCurve)

        mockMvc.get("/api/v1/backtests/${created.id}/trades") { header("Authorization", "Bearer ${otherAuth.accessToken}") }
            .andExpect { status { isNotFound() } }
    }
}
