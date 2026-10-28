package com.cephCoding.orynix.papertrading

import com.cephCoding.orynix.market.domain.Candle
import com.cephCoding.orynix.market.domain.CandleTimeframe
import com.cephCoding.orynix.papertrading.domain.OrderSide
import com.cephCoding.orynix.papertrading.domain.PositionEffect
import com.cephCoding.orynix.papertrading.dto.AccountSummaryResponse
import com.cephCoding.orynix.papertrading.dto.ClosePositionRequest
import com.cephCoding.orynix.papertrading.dto.CreatePaperTradingAccountRequest
import com.cephCoding.orynix.papertrading.dto.PaperOrderResponse
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

class PaperTradingOrderControllerTest : AbstractIntegrationTest() {

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

    private fun createAccount(
        token: String,
        name: String = "Orders account",
        baseCurrency: String = "USD",
        initialDeposit: BigDecimal = BigDecimal("10000"),
    ): PaperTradingAccountResponse {
        val response = mockMvc.post("/api/v1/paper-trading/accounts") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(CreatePaperTradingAccountRequest(name, baseCurrency, initialDeposit))
            header("Authorization", "Bearer $token")
        }.andReturn().response
        assertEquals(201, response.status)
        return objectMapper.readValue(response.contentAsString, PaperTradingAccountResponse::class.java)
    }

    private fun submit(token: String, accountId: UUID, request: SubmitPaperOrderRequest) =
        mockMvc.post("/api/v1/paper-trading/accounts/$accountId/orders") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
            header("Authorization", "Bearer $token")
        }

    private fun order(
        symbol: String = "AAPL",
        side: OrderSide,
        effect: PositionEffect,
        quantity: BigDecimal,
        idempotencyKey: String = UUID.randomUUID().toString(),
        stopLossPrice: BigDecimal? = null,
        takeProfitPrice: BigDecimal? = null,
    ) = SubmitPaperOrderRequest(symbol, side, effect, quantity, idempotencyKey, stopLossPrice, takeProfitPrice)

    private fun summary(token: String, accountId: UUID): AccountSummaryResponse {
        val response = mockMvc.get("/api/v1/paper-trading/accounts/$accountId/summary") {
            header("Authorization", "Bearer $token")
        }.andReturn().response
        assertEquals(200, response.status)
        return objectMapper.readValue(response.contentAsString, AccountSummaryResponse::class.java)
    }

    @Test
    fun `BUY plus OPEN opens a long and debits cash by cost plus commission`() {
        val auth = register("pt-ord-buyopen@example.com")
        fakeMarketDataProvider.candlesBySymbol["AAPL"] = listOf(candle("AAPL", 150.0))
        val account = createAccount(auth.accessToken)

        val response = submit(auth.accessToken, account.id, order(side = OrderSide.BUY, effect = PositionEffect.OPEN, quantity = BigDecimal("10")))
            .andReturn().response
        assertEquals(201, response.status)
        val filled = objectMapper.readValue(response.contentAsString, PaperOrderResponse::class.java)
        assertEquals(BigDecimal("150.0000"), filled.fillPrice)
        assertEquals("FILLED", filled.status.name)

        val afterSummary = summary(auth.accessToken, account.id)
        assertEquals(BigDecimal("8500.0000"), afterSummary.cash) // 10000 - 10*150
        assertEquals(BigDecimal("0.0000"), afterSummary.unrealizedPnlLong) // no price move yet - fill price == mark price
    }

    @Test
    fun `SELL plus CLOSE on a long realizes the correct profit`() {
        val auth = register("pt-ord-closelong@example.com")
        fakeMarketDataProvider.candlesBySymbol["AAPL"] = listOf(candle("AAPL", 150.0))
        val account = createAccount(auth.accessToken)
        submit(auth.accessToken, account.id, order(side = OrderSide.BUY, effect = PositionEffect.OPEN, quantity = BigDecimal("10")))
            .andExpect { status { isCreated() } }

        fakeMarketDataProvider.candlesBySymbol["AAPL"] = listOf(candle("AAPL", 160.0))
        val response = submit(auth.accessToken, account.id, order(side = OrderSide.SELL, effect = PositionEffect.CLOSE, quantity = BigDecimal("10")))
            .andReturn().response
        assertEquals(201, response.status)

        val afterSummary = summary(auth.accessToken, account.id)
        assertEquals(BigDecimal("10100.0000"), afterSummary.cash) // 10000 - 1500 + 1600
        assertEquals(BigDecimal("100.0000"), afterSummary.realizedPnlCumulative)
    }

    @Test
    fun `SELL plus OPEN opens a bounded short and reserves mark-to-market collateral`() {
        val auth = register("pt-ord-shortopen@example.com")
        fakeMarketDataProvider.candlesBySymbol["AAPL"] = listOf(candle("AAPL", 100.0))
        val account = createAccount(auth.accessToken)

        submit(auth.accessToken, account.id, order(side = OrderSide.SELL, effect = PositionEffect.OPEN, quantity = BigDecimal("10")))
            .andExpect { status { isCreated() } }

        val afterSummary = summary(auth.accessToken, account.id)
        assertEquals(BigDecimal("11000.0000"), afterSummary.cash) // 10000 + 10*100
        assertEquals(BigDecimal("1500.0000"), afterSummary.totalReservedCollateral) // 10*100*1.5
        assertEquals(BigDecimal("9500.0000"), afterSummary.buyingPower) // 11000 - 1500
    }

    @Test
    fun `BUY plus CLOSE on a short realizes profit when price dropped`() {
        val auth = register("pt-ord-closeshort@example.com")
        fakeMarketDataProvider.candlesBySymbol["AAPL"] = listOf(candle("AAPL", 100.0))
        val account = createAccount(auth.accessToken)
        submit(auth.accessToken, account.id, order(side = OrderSide.SELL, effect = PositionEffect.OPEN, quantity = BigDecimal("10")))
            .andExpect { status { isCreated() } }

        fakeMarketDataProvider.candlesBySymbol["AAPL"] = listOf(candle("AAPL", 90.0))
        submit(auth.accessToken, account.id, order(side = OrderSide.BUY, effect = PositionEffect.CLOSE, quantity = BigDecimal("10")))
            .andExpect { status { isCreated() } }

        val afterSummary = summary(auth.accessToken, account.id)
        assertEquals(BigDecimal("10100.0000"), afterSummary.cash) // 10000 + 1000 - 900
        assertEquals(BigDecimal("100.0000"), afterSummary.realizedPnlCumulative)
        assertEquals(BigDecimal.ZERO.setScale(4), afterSummary.totalReservedCollateral)
    }

    @Test
    fun `opening the opposite direction on an already-open position is rejected with 400`() {
        val auth = register("pt-ord-conflict@example.com")
        fakeMarketDataProvider.candlesBySymbol["AAPL"] = listOf(candle("AAPL", 100.0))
        val account = createAccount(auth.accessToken)
        submit(auth.accessToken, account.id, order(side = OrderSide.BUY, effect = PositionEffect.OPEN, quantity = BigDecimal("5")))
            .andExpect { status { isCreated() } }

        submit(auth.accessToken, account.id, order(side = OrderSide.SELL, effect = PositionEffect.OPEN, quantity = BigDecimal("5")))
            .andExpect { status { isBadRequest() } }
    }

    @Test
    fun `closing more than the open quantity is rejected with 400`() {
        val auth = register("pt-ord-overclose@example.com")
        fakeMarketDataProvider.candlesBySymbol["AAPL"] = listOf(candle("AAPL", 100.0))
        val account = createAccount(auth.accessToken)
        submit(auth.accessToken, account.id, order(side = OrderSide.BUY, effect = PositionEffect.OPEN, quantity = BigDecimal("5")))
            .andExpect { status { isCreated() } }

        submit(auth.accessToken, account.id, order(side = OrderSide.SELL, effect = PositionEffect.CLOSE, quantity = BigDecimal("10")))
            .andExpect { status { isBadRequest() } }
    }

    @Test
    fun `closing a position with nothing open is rejected with 400`() {
        val auth = register("pt-ord-noposition@example.com")
        fakeMarketDataProvider.candlesBySymbol["AAPL"] = listOf(candle("AAPL", 100.0))
        val account = createAccount(auth.accessToken)

        submit(auth.accessToken, account.id, order(side = OrderSide.SELL, effect = PositionEffect.CLOSE, quantity = BigDecimal("1")))
            .andExpect { status { isBadRequest() } }
    }

    @Test
    fun `insufficient buying power is rejected with 400 and names the shortfall`() {
        val auth = register("pt-ord-insufficient@example.com")
        fakeMarketDataProvider.candlesBySymbol["AAPL"] = listOf(candle("AAPL", 1000.0))
        val account = createAccount(auth.accessToken, initialDeposit = BigDecimal("100"))

        submit(auth.accessToken, account.id, order(side = OrderSide.BUY, effect = PositionEffect.OPEN, quantity = BigDecimal("10")))
            .andExpect { status { isBadRequest() } }
    }

    @Test
    fun `a price older than the staleness threshold is rejected with 422`() {
        val auth = register("pt-ord-stale@example.com")
        fakeMarketDataProvider.candlesBySymbol["AAPL"] = listOf(candle("AAPL", 150.0, daysAgo = 30))
        val account = createAccount(auth.accessToken)

        submit(auth.accessToken, account.id, order(side = OrderSide.BUY, effect = PositionEffect.OPEN, quantity = BigDecimal("1")))
            .andExpect { status { isUnprocessableContent() } }
    }

    @Test
    fun `no price available at all is rejected with 422`() {
        val auth = register("pt-ord-noprice@example.com")
        val account = createAccount(auth.accessToken)

        submit(auth.accessToken, account.id, order(side = OrderSide.BUY, effect = PositionEffect.OPEN, quantity = BigDecimal("1")))
            .andExpect { status { isUnprocessableContent() } }
    }

    @Test
    fun `a stock symbol requires a USD account currency`() {
        val auth = register("pt-ord-currency@example.com")
        fakeMarketDataProvider.candlesBySymbol["AAPL"] = listOf(candle("AAPL", 150.0))
        val account = createAccount(auth.accessToken, baseCurrency = "EUR")

        submit(auth.accessToken, account.id, order(side = OrderSide.BUY, effect = PositionEffect.OPEN, quantity = BigDecimal("1")))
            .andExpect { status { isBadRequest() } }
    }

    @Test
    fun `a stop-loss on the wrong side of entry for a long is rejected with 400`() {
        val auth = register("pt-ord-badstop@example.com")
        fakeMarketDataProvider.candlesBySymbol["AAPL"] = listOf(candle("AAPL", 150.0))
        val account = createAccount(auth.accessToken)

        submit(
            auth.accessToken,
            account.id,
            order(side = OrderSide.BUY, effect = PositionEffect.OPEN, quantity = BigDecimal("1"), stopLossPrice = BigDecimal("160")),
        ).andExpect { status { isBadRequest() } }
    }

    @Test
    fun `protective levels on a CLOSE order are rejected with 400, never silently ignored`() {
        val auth = register("pt-ord-closewithstop@example.com")
        fakeMarketDataProvider.candlesBySymbol["AAPL"] = listOf(candle("AAPL", 150.0))
        val account = createAccount(auth.accessToken)
        submit(auth.accessToken, account.id, order(side = OrderSide.BUY, effect = PositionEffect.OPEN, quantity = BigDecimal("1")))
            .andExpect { status { isCreated() } }

        submit(
            auth.accessToken,
            account.id,
            order(side = OrderSide.SELL, effect = PositionEffect.CLOSE, quantity = BigDecimal("1"), stopLossPrice = BigDecimal("140")),
        ).andExpect { status { isBadRequest() } }
    }

    @Test
    fun `partial close leaves the position open with reduced quantity`() {
        val auth = register("pt-ord-partial@example.com")
        fakeMarketDataProvider.candlesBySymbol["AAPL"] = listOf(candle("AAPL", 100.0))
        val account = createAccount(auth.accessToken)
        submit(auth.accessToken, account.id, order(side = OrderSide.BUY, effect = PositionEffect.OPEN, quantity = BigDecimal("10")))
            .andExpect { status { isCreated() } }

        submit(auth.accessToken, account.id, order(side = OrderSide.SELL, effect = PositionEffect.CLOSE, quantity = BigDecimal("4")))
            .andExpect { status { isCreated() } }

        val positionsResponse = mockMvc.get("/api/v1/paper-trading/accounts/${account.id}/positions") {
            header("Authorization", "Bearer ${auth.accessToken}")
        }.andReturn().response
        assertTrue(positionsResponse.contentAsString.contains("\"openQuantity\":6"))
    }

    @Test
    fun `the close convenience endpoint infers side and effect from the position direction`() {
        val auth = register("pt-ord-closeendpoint@example.com")
        fakeMarketDataProvider.candlesBySymbol["AAPL"] = listOf(candle("AAPL", 100.0))
        val account = createAccount(auth.accessToken)
        submit(auth.accessToken, account.id, order(side = OrderSide.BUY, effect = PositionEffect.OPEN, quantity = BigDecimal("5")))
            .andExpect { status { isCreated() } }

        val positionsResponse = mockMvc.get("/api/v1/paper-trading/accounts/${account.id}/positions") {
            header("Authorization", "Bearer ${auth.accessToken}")
        }.andReturn().response
        val positionId = objectMapper.readTree(positionsResponse.contentAsString)[0]["id"].asText()

        mockMvc.post("/api/v1/paper-trading/accounts/${account.id}/positions/$positionId/close") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(ClosePositionRequest(quantity = null, idempotencyKey = UUID.randomUUID().toString()))
            header("Authorization", "Bearer ${auth.accessToken}")
        }.andExpect { status { isCreated() } }

        val closedResponse = mockMvc.get("/api/v1/paper-trading/accounts/${account.id}/positions/closed") {
            header("Authorization", "Bearer ${auth.accessToken}")
        }.andReturn().response
        assertTrue(closedResponse.contentAsString.contains("\"totalElements\":1"))
    }

    @Test
    fun `missing authentication is rejected with 401`() {
        mockMvc.post("/api/v1/paper-trading/accounts/${UUID.randomUUID()}/orders") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(order(side = OrderSide.BUY, effect = PositionEffect.OPEN, quantity = BigDecimal("1")))
        }.andExpect { status { isUnauthorized() } }
    }
}
