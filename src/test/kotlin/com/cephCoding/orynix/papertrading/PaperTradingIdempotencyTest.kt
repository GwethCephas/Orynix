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
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Executors

class PaperTradingIdempotencyTest : AbstractIntegrationTest() {

    private fun seedAccount(email: String): Pair<String, PaperTradingAccountResponse> {
        val auth = register(email)
        fakeMarketDataProvider.candlesBySymbol["AAPL"] = listOf(
            Candle(
                symbol = "AAPL",
                timeframe = CandleTimeframe.DAILY,
                timestamp = Instant.now(),
                open = BigDecimal("150"),
                high = BigDecimal("151"),
                low = BigDecimal("149"),
                close = BigDecimal("150"),
                volume = BigDecimal("1000000"),
            ),
        )
        val response = mockMvc.post("/api/v1/paper-trading/accounts") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(CreatePaperTradingAccountRequest("Idempotency account", "USD", BigDecimal("10000")))
            header("Authorization", "Bearer ${auth.accessToken}")
        }.andReturn().response
        return auth.accessToken to objectMapper.readValue(response.contentAsString, PaperTradingAccountResponse::class.java)
    }

    private fun submit(token: String, accountId: UUID, idempotencyKey: String) =
        mockMvc.post("/api/v1/paper-trading/accounts/$accountId/orders") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                SubmitPaperOrderRequest("AAPL", OrderSide.BUY, PositionEffect.OPEN, BigDecimal("10"), idempotencyKey, null, null),
            )
            header("Authorization", "Bearer $token")
        }.andReturn().response

    @Test
    fun `replaying the same idempotency key sequentially returns the identical order, never a second fill`() {
        val (token, account) = seedAccount("pt-idem-seq@example.com")
        val key = UUID.randomUUID().toString()

        val first = objectMapper.readValue(submit(token, account.id, key).contentAsString, PaperOrderResponse::class.java)
        val second = objectMapper.readValue(submit(token, account.id, key).contentAsString, PaperOrderResponse::class.java)

        assertEquals(first.id, second.id)

        val summaryResponse = mockMvc.get("/api/v1/paper-trading/accounts/${account.id}/summary") {
            header("Authorization", "Bearer $token")
        }.andReturn().response
        val summary = objectMapper.readValue(summaryResponse.contentAsString, AccountSummaryResponse::class.java)
        assertEquals(BigDecimal("8500.0000"), summary.cash) // 10000 - 10*150, only once

        val ledgerResponse = mockMvc.get("/api/v1/paper-trading/accounts/${account.id}/ledger") {
            header("Authorization", "Bearer $token")
        }.andReturn().response
        assertEquals(1, objectMapper.readTree(ledgerResponse.contentAsString)["totalElements"].asInt())
    }

    @Test
    fun `replaying the same idempotency key concurrently still produces exactly one fill`() {
        val (token, account) = seedAccount("pt-idem-concurrent@example.com")
        val key = UUID.randomUUID().toString()

        val executor = Executors.newFixedThreadPool(2)
        val futures = (1..2).map { executor.submit<String> { submit(token, account.id, key).contentAsString } }
        val orders = futures.map { objectMapper.readValue(it.get(), PaperOrderResponse::class.java) }
        executor.shutdown()

        assertEquals(orders[0].id, orders[1].id)

        val ledgerResponse = mockMvc.get("/api/v1/paper-trading/accounts/${account.id}/ledger") {
            header("Authorization", "Bearer $token")
        }.andReturn().response
        assertEquals(1, objectMapper.readTree(ledgerResponse.contentAsString)["totalElements"].asInt())
    }

    @Test
    fun `replaying a close request after the position already closed returns the original order, not a 400`() {
        val (token, account) = seedAccount("pt-idem-close@example.com")
        val openResponse = submit(token, account.id, UUID.randomUUID().toString())
        val opened = objectMapper.readValue(openResponse.contentAsString, PaperOrderResponse::class.java)

        val closeKey = UUID.randomUUID().toString()
        val firstClose = mockMvc.post("/api/v1/paper-trading/accounts/${account.id}/positions/${opened.positionId}/close") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(ClosePositionRequest(quantity = null, idempotencyKey = closeKey))
            header("Authorization", "Bearer $token")
        }.andReturn().response
        assertEquals(201, firstClose.status)
        val firstCloseOrder = objectMapper.readValue(firstClose.contentAsString, PaperOrderResponse::class.java)

        // The position is now CLOSED - replaying the SAME close request must still return the
        // original close order (the actual idempotency contract), not a spurious "not open" error.
        val replayedClose = mockMvc.post("/api/v1/paper-trading/accounts/${account.id}/positions/${opened.positionId}/close") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(ClosePositionRequest(quantity = null, idempotencyKey = closeKey))
            header("Authorization", "Bearer $token")
        }.andReturn().response
        assertEquals(201, replayedClose.status)
        val replayedCloseOrder = objectMapper.readValue(replayedClose.contentAsString, PaperOrderResponse::class.java)
        assertEquals(firstCloseOrder.id, replayedCloseOrder.id)
    }
}
