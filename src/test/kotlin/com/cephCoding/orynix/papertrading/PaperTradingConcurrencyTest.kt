package com.cephCoding.orynix.papertrading

import com.cephCoding.orynix.market.domain.Candle
import com.cephCoding.orynix.market.domain.CandleTimeframe
import com.cephCoding.orynix.papertrading.domain.OrderSide
import com.cephCoding.orynix.papertrading.domain.PositionEffect
import com.cephCoding.orynix.papertrading.dto.CreatePaperTradingAccountRequest
import com.cephCoding.orynix.papertrading.dto.PaperTradingAccountResponse
import com.cephCoding.orynix.papertrading.dto.SubmitPaperOrderRequest
import com.cephCoding.orynix.support.AbstractIntegrationTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.post
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Executors

/**
 * Proves the account-level pessimistic lock (CLAUDE.md Section 22) actually serializes two
 * concurrent orders racing for the same cash - each sized to exactly exhaust the account's
 * remaining buying power, so at most one can succeed no matter how the race lands.
 */
class PaperTradingConcurrencyTest : AbstractIntegrationTest() {

    @Test
    fun `two concurrent BUY plus OPEN orders racing for the same buying power - exactly one succeeds`() {
        val auth = register("pt-race@example.com")
        fakeMarketDataProvider.candlesBySymbol["AAPL"] = listOf(
            Candle(
                symbol = "AAPL",
                timeframe = CandleTimeframe.DAILY,
                timestamp = Instant.now(),
                open = BigDecimal("1000"),
                high = BigDecimal("1001"),
                low = BigDecimal("999"),
                close = BigDecimal("1000"),
                volume = BigDecimal("1000000"),
            ),
        )

        val createResponse = mockMvc.post("/api/v1/paper-trading/accounts") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(CreatePaperTradingAccountRequest("Race account", "USD", BigDecimal("10000")))
            header("Authorization", "Bearer ${auth.accessToken}")
        }.andReturn().response
        val account = objectMapper.readValue(createResponse.contentAsString, PaperTradingAccountResponse::class.java)

        // Each order costs 6*1000 = 6000; only one of two can fit in a 10000 cash account.
        fun orderRequest() = SubmitPaperOrderRequest(
            symbol = "AAPL",
            side = OrderSide.BUY,
            positionEffect = PositionEffect.OPEN,
            quantity = BigDecimal("6"),
            idempotencyKey = UUID.randomUUID().toString(),
            stopLossPrice = null,
            takeProfitPrice = null,
        )

        val executor = Executors.newFixedThreadPool(2)
        val futures = (1..2).map {
            executor.submit<Int> {
                mockMvc.post("/api/v1/paper-trading/accounts/${account.id}/orders") {
                    contentType = MediaType.APPLICATION_JSON
                    content = objectMapper.writeValueAsString(orderRequest())
                    header("Authorization", "Bearer ${auth.accessToken}")
                }.andReturn().response.status
            }
        }
        val statuses = futures.map { it.get() }
        executor.shutdown()

        assertEquals(1, statuses.count { it == 201 }, "exactly one order should have been filled")
        assertEquals(1, statuses.count { it == 400 }, "exactly one order should have been rejected for insufficient buying power")
    }
}
