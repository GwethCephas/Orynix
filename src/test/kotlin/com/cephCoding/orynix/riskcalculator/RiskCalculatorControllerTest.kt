package com.cephCoding.orynix.riskcalculator

import com.cephCoding.orynix.riskcalculator.domain.RiskCalculationStatus
import com.cephCoding.orynix.riskcalculator.dto.RiskCalculationRequest
import com.cephCoding.orynix.riskcalculator.dto.RiskCalculationResponse
import com.cephCoding.orynix.support.AbstractIntegrationTest
import com.cephCoding.orynix.tradingaccount.domain.TradingPlatform
import com.cephCoding.orynix.tradingaccount.dto.TradingAccountProfileRequest
import com.cephCoding.orynix.tradingaccount.dto.TradingAccountProfileResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.post
import java.math.BigDecimal
import java.util.UUID

class RiskCalculatorControllerTest : AbstractIntegrationTest() {

    private fun baseRequest(
        symbol: String = "AAPL",
        accountProfileId: UUID? = null,
        equity: BigDecimal? = BigDecimal("10000"),
        accountCurrency: String? = "USD",
        riskPercentPerTrade: BigDecimal? = BigDecimal("1"),
        entryPrice: BigDecimal = BigDecimal("50"),
        stopLossPrice: BigDecimal = BigDecimal("48"),
        takeProfitPrice: BigDecimal? = BigDecimal("56"),
    ) = RiskCalculationRequest(
        symbol = symbol,
        accountProfileId = accountProfileId,
        equity = equity,
        accountCurrency = accountCurrency,
        riskPercentPerTrade = riskPercentPerTrade,
        entryPrice = entryPrice,
        stopLossPrice = stopLossPrice,
        takeProfitPrice = takeProfitPrice,
    )

    private fun calculate(token: String, request: RiskCalculationRequest) = mockMvc.post("/api/v1/risk-calculations") {
        contentType = MediaType.APPLICATION_JSON
        content = objectMapper.writeValueAsString(request)
        header("Authorization", "Bearer $token")
    }

    private fun createProfile(
        token: String,
        accountCurrency: String = "USD",
        balance: BigDecimal = BigDecimal("20000"),
        riskPercentPerTrade: BigDecimal = BigDecimal("2"),
    ): TradingAccountProfileResponse {
        val request = TradingAccountProfileRequest(
            name = "Calculator profile",
            platform = TradingPlatform.OTHER,
            platformDetails = null,
            accountCurrency = accountCurrency,
            balance = balance,
            riskPercentPerTrade = riskPercentPerTrade,
            maxDailyLossPercent = null,
            maxConcurrentPositions = null,
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
    fun `a hand-computed long setup with a take-profit returns exact sizing and R R`() {
        val auth = register("risk-handcomputed@example.com")

        // equity 10000, risk 1% -> budget 100; stopDistance 50-48=2 -> quantity 50;
        // estimatedLossAtStop 50*2=100; estimatedGainAtTakeProfit 50*(56-50)=300; R:R (56-50)/(50-48)=3
        val response = calculate(auth.accessToken, baseRequest()).andReturn().response
        assertEquals(200, response.status)
        val body = objectMapper.readValue(response.contentAsString, RiskCalculationResponse::class.java)

        assertEquals(RiskCalculationStatus.OK, body.status)
        assertEquals(BigDecimal("2.0000"), body.stopDistance)
        assertEquals(BigDecimal("100.0000"), body.plannedMonetaryRisk)
        assertEquals(BigDecimal("50.00000000"), body.quantity)
        assertEquals(BigDecimal("100.0000"), body.estimatedLossAtStop)
        assertEquals(BigDecimal("300.0000"), body.estimatedGainAtTakeProfit)
        assertEquals(BigDecimal("3.0000"), body.riskRewardRatio)
        assertEquals("AAPL", body.symbol)
    }

    @Test
    fun `without a take-profit, R R and estimated gain are null but status is still OK`() {
        val auth = register("risk-notp@example.com")

        val response = calculate(auth.accessToken, baseRequest(takeProfitPrice = null)).andReturn().response
        val body = objectMapper.readValue(response.contentAsString, RiskCalculationResponse::class.java)

        assertEquals(RiskCalculationStatus.OK, body.status)
        assertNull(body.takeProfitPrice)
        assertNull(body.estimatedGainAtTakeProfit)
        assertNull(body.riskRewardRatio)
        assertEquals(BigDecimal("50.00000000"), body.quantity)
    }

    @Test
    fun `a risk budget smaller than one unit's risk returns UNSUPPORTED with a null quantity, never a fabricated zero`() {
        val auth = register("risk-unsupported@example.com")

        val request = baseRequest(
            equity = BigDecimal("1"),
            riskPercentPerTrade = BigDecimal("0.0001"),
            entryPrice = BigDecimal("10001"),
            stopLossPrice = BigDecimal("1"),
            takeProfitPrice = null,
        )
        val response = calculate(auth.accessToken, request).andReturn().response
        assertEquals(200, response.status)
        val body = objectMapper.readValue(response.contentAsString, RiskCalculationResponse::class.java)

        assertEquals(RiskCalculationStatus.UNSUPPORTED, body.status)
        assertNull(body.quantity)
        assertNull(body.estimatedLossAtStop)
        assertNull(body.estimatedGainAtTakeProfit)
        assertTrue(body.limitations.isNotEmpty())
    }

    @Test
    fun `a stop at or above entry is rejected with 400 (long-only)`() {
        val auth = register("risk-badstop@example.com")
        calculate(auth.accessToken, baseRequest(stopLossPrice = BigDecimal("50"))).andExpect { status { isBadRequest() } }
        calculate(auth.accessToken, baseRequest(stopLossPrice = BigDecimal("52"))).andExpect { status { isBadRequest() } }
    }

    @Test
    fun `a take-profit at or below entry is rejected with 400 (long-only)`() {
        val auth = register("risk-badtp@example.com")
        calculate(auth.accessToken, baseRequest(takeProfitPrice = BigDecimal("50"))).andExpect { status { isBadRequest() } }
        calculate(auth.accessToken, baseRequest(takeProfitPrice = BigDecimal("40"))).andExpect { status { isBadRequest() } }
    }

    @Test
    fun `a currency mismatch against the instrument is rejected with 400`() {
        val auth = register("risk-badcurrency@example.com")
        calculate(auth.accessToken, baseRequest(symbol = "AAPL", accountCurrency = "EUR")).andExpect { status { isBadRequest() } }
    }

    @Test
    fun `missing equity, riskPercentPerTrade, or accountCurrency without a profile is rejected with 400`() {
        val auth = register("risk-missingfields@example.com")
        calculate(auth.accessToken, baseRequest(equity = null)).andExpect { status { isBadRequest() } }
        calculate(auth.accessToken, baseRequest(riskPercentPerTrade = null)).andExpect { status { isBadRequest() } }
        calculate(auth.accessToken, baseRequest(accountCurrency = null)).andExpect { status { isBadRequest() } }
    }

    @Test
    fun `a selected profile supplies equity, riskPercentPerTrade, and accountCurrency when the request omits them`() {
        val auth = register("risk-profile@example.com")
        val profile = createProfile(auth.accessToken, balance = BigDecimal("20000"), riskPercentPerTrade = BigDecimal("2"))

        // budget = 20000 * 2% = 400; stopDistance 2 -> quantity 200
        val request = baseRequest(equity = null, accountCurrency = null, riskPercentPerTrade = null, accountProfileId = profile.id)
        val response = calculate(auth.accessToken, request).andReturn().response
        assertEquals(200, response.status)
        val body = objectMapper.readValue(response.contentAsString, RiskCalculationResponse::class.java)

        assertEquals(0, BigDecimal("20000").compareTo(body.equity))
        assertEquals(0, BigDecimal("2").compareTo(body.riskPercentPerTrade))
        assertEquals("USD", body.accountCurrency)
        assertEquals(0, BigDecimal("200").compareTo(body.quantity))
    }

    @Test
    fun `an explicit field in the request wins over the same field on the selected profile`() {
        val auth = register("risk-override@example.com")
        val profile = createProfile(auth.accessToken, balance = BigDecimal("20000"), riskPercentPerTrade = BigDecimal("2"))

        val request = baseRequest(equity = BigDecimal("5000"), accountCurrency = null, riskPercentPerTrade = null, accountProfileId = profile.id)
        val response = calculate(auth.accessToken, request).andReturn().response
        val body = objectMapper.readValue(response.contentAsString, RiskCalculationResponse::class.java)

        assertEquals(0, BigDecimal("5000").compareTo(body.equity))
        assertEquals(0, BigDecimal("2").compareTo(body.riskPercentPerTrade))
    }

    @Test
    fun `an archived profile can no longer be referenced for a risk calculation`() {
        val auth = register("risk-archived@example.com")
        val profile = createProfile(auth.accessToken)

        mockMvc.delete("/api/v1/trading-accounts/${profile.id}") {
            header("Authorization", "Bearer ${auth.accessToken}")
        }.andExpect { status { isNoContent() } }

        val request = baseRequest(equity = null, accountCurrency = null, riskPercentPerTrade = null, accountProfileId = profile.id)
        calculate(auth.accessToken, request).andExpect { status { isNotFound() } }
    }

    @Test
    fun `referencing another user's profile returns 404`() {
        val authA = register("risk-ownera@example.com")
        val authB = register("risk-ownerb@example.com")
        val profile = createProfile(authA.accessToken)

        val request = baseRequest(equity = null, accountCurrency = null, riskPercentPerTrade = null, accountProfileId = profile.id)
        calculate(authB.accessToken, request).andExpect { status { isNotFound() } }
    }

    @Test
    fun `never emits a BUY or SELL recommendation field`() {
        val auth = register("risk-norecommendation@example.com")
        val response = calculate(auth.accessToken, baseRequest()).andReturn().response
        assertEquals(200, response.status)
        assertTrue(!response.contentAsString.contains("recommendation", ignoreCase = true))
        assertTrue(!response.contentAsString.contains("\"action\"", ignoreCase = true))
    }
}
