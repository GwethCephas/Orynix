package com.cephCoding.orynix.papertrading

import com.cephCoding.orynix.market.domain.Candle
import com.cephCoding.orynix.market.domain.CandleTimeframe
import com.cephCoding.orynix.papertrading.domain.OrderSide
import com.cephCoding.orynix.papertrading.domain.PositionEffect
import com.cephCoding.orynix.papertrading.dto.AccountSummaryResponse
import com.cephCoding.orynix.papertrading.dto.CreatePaperTradingAccountRequest
import com.cephCoding.orynix.papertrading.dto.PaperTradingAccountResponse
import com.cephCoding.orynix.papertrading.dto.SubmitPaperOrderRequest
import com.cephCoding.orynix.support.AbstractIntegrationTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

class PaperTradingValuationIntegrationTest : AbstractIntegrationTest() {

    private fun candle(symbol: String, close: Double, daysAgo: Long = 0): Candle = Candle(
        symbol = symbol,
        timeframe = CandleTimeframe.DAILY,
        timestamp = Instant.now().minus(daysAgo, ChronoUnit.DAYS),
        open = BigDecimal.valueOf(close),
        high = BigDecimal.valueOf(close + 1.0),
        low = BigDecimal.valueOf(close - 1.0),
        close = BigDecimal.valueOf(close),
        volume = BigDecimal.valueOf(1_000_000.0),
    )

    private fun createAccount(token: String): PaperTradingAccountResponse {
        val response = mockMvc.post("/api/v1/paper-trading/accounts") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(CreatePaperTradingAccountRequest("Valuation account", "USD", BigDecimal("10000")))
            header("Authorization", "Bearer $token")
        }.andReturn().response
        return objectMapper.readValue(response.contentAsString, PaperTradingAccountResponse::class.java)
    }

    private fun summary(token: String, accountId: UUID): AccountSummaryResponse {
        val response = mockMvc.get("/api/v1/paper-trading/accounts/$accountId/summary") {
            header("Authorization", "Bearer $token")
        }.andReturn().response
        return objectMapper.readValue(response.contentAsString, AccountSummaryResponse::class.java)
    }

    @Test
    fun `summary equity tracks an open long's mark-to-market move exactly`() {
        val auth = register("pt-val-long@example.com")
        fakeMarketDataProvider.candlesBySymbol["AAPL"] = listOf(candle("AAPL", 150.0))
        val account = createAccount(auth.accessToken)

        mockMvc.post("/api/v1/paper-trading/accounts/${account.id}/orders") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                SubmitPaperOrderRequest("AAPL", OrderSide.BUY, PositionEffect.OPEN, BigDecimal("10"), UUID.randomUUID().toString(), null, null),
            )
            header("Authorization", "Bearer ${auth.accessToken}")
        }.andExpect { status { isCreated() } }

        // Default commission is zero in this test (never set on the account), so equity right
        // after opening at the same price as the fill is exactly the initial deposit - no drag.
        val beforeMark = summary(auth.accessToken, account.id)
        assertEquals(BigDecimal("8500.0000"), beforeMark.cash)
        assertEquals(BigDecimal("10000.0000"), beforeMark.equity)

        fakeMarketDataProvider.candlesBySymbol["AAPL"] = listOf(candle("AAPL", 160.0))
        val afterMark = summary(auth.accessToken, account.id)
        assertEquals(BigDecimal("10100.0000"), afterMark.equity) // 8500 + 10*160
        assertEquals(BigDecimal("100.0000"), afterMark.unrealizedPnlLong)
    }

    @Test
    fun `a stale price degrades that position's valuation without blanking the rest of the account`() {
        val auth = register("pt-val-stale@example.com")
        fakeMarketDataProvider.candlesBySymbol["AAPL"] = listOf(candle("AAPL", 150.0))
        val account = createAccount(auth.accessToken)

        mockMvc.post("/api/v1/paper-trading/accounts/${account.id}/orders") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                SubmitPaperOrderRequest("AAPL", OrderSide.BUY, PositionEffect.OPEN, BigDecimal("10"), UUID.randomUUID().toString(), null, null),
            )
            header("Authorization", "Bearer ${auth.accessToken}")
        }.andExpect { status { isCreated() } }

        // Now the only available candle is far older than the staleness threshold.
        fakeMarketDataProvider.candlesBySymbol["AAPL"] = listOf(candle("AAPL", 150.0, daysAgo = 30))

        val response = mockMvc.get("/api/v1/paper-trading/accounts/${account.id}/positions") {
            header("Authorization", "Bearer ${auth.accessToken}")
        }.andReturn().response
        assertEquals(200, response.status) // the account/position listing never fails outright
        assertTrue(response.contentAsString.contains("\"priceStatus\":\"STALE\""))

        val summaryResponse = mockMvc.get("/api/v1/paper-trading/accounts/${account.id}/summary") {
            header("Authorization", "Bearer ${auth.accessToken}")
        }.andReturn().response
        assertEquals(200, summaryResponse.status)
        val summary = objectMapper.readValue(summaryResponse.contentAsString, AccountSummaryResponse::class.java)
        assertTrue(summary.valuationWarnings.isNotEmpty())
        assertEquals(BigDecimal("8500.0000"), summary.cash) // cash itself is unaffected, always exact
    }
}
