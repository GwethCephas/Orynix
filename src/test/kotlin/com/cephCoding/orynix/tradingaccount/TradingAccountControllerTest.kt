package com.cephCoding.orynix.tradingaccount

import com.cephCoding.orynix.support.AbstractIntegrationTest
import com.cephCoding.orynix.tradingaccount.domain.TradingPlatform
import com.cephCoding.orynix.tradingaccount.dto.TradingAccountProfileRequest
import com.cephCoding.orynix.tradingaccount.dto.TradingAccountProfileResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import java.math.BigDecimal
import java.util.UUID

class TradingAccountControllerTest : AbstractIntegrationTest() {

    private fun profileRequest(
        name: String = "Main account",
        platform: TradingPlatform = TradingPlatform.METATRADER_5,
        platformDetails: String? = "Demo-12345",
        accountCurrency: String = "USD",
        balance: BigDecimal = BigDecimal("10000.00"),
        riskPercentPerTrade: BigDecimal? = null,
        maxDailyLossPercent: BigDecimal? = null,
        maxConcurrentPositions: Int? = null,
    ) = TradingAccountProfileRequest(
        name = name,
        platform = platform,
        platformDetails = platformDetails,
        accountCurrency = accountCurrency,
        balance = balance,
        riskPercentPerTrade = riskPercentPerTrade,
        maxDailyLossPercent = maxDailyLossPercent,
        maxConcurrentPositions = maxConcurrentPositions,
    )

    private fun postProfile(token: String, request: TradingAccountProfileRequest) = mockMvc.post("/api/v1/trading-accounts") {
        contentType = MediaType.APPLICATION_JSON
        content = objectMapper.writeValueAsString(request)
        header("Authorization", "Bearer $token")
    }

    private fun createProfile(token: String, request: TradingAccountProfileRequest = profileRequest()): TradingAccountProfileResponse {
        val response = postProfile(token, request).andReturn().response
        assertEquals(201, response.status)
        return objectMapper.readValue(response.contentAsString, TradingAccountProfileResponse::class.java)
    }

    @Test
    fun `creating a profile without riskPercentPerTrade defaults it to 1 percent`() {
        val auth = register("ta-default-risk@example.com")
        val profile = createProfile(auth.accessToken)

        assertEquals(BigDecimal("1.00"), profile.riskPercentPerTrade)
        assertFalse(profile.brokerVerified)
        assertFalse(profile.archived)
    }

    @Test
    fun `brokerVerified and the enforcement disclosure fields are always false`() {
        val auth = register("ta-brokerverified@example.com")
        val profile = createProfile(
            auth.accessToken,
            profileRequest(maxDailyLossPercent = BigDecimal("5"), maxConcurrentPositions = 3),
        )

        assertFalse(profile.brokerVerified)
        assertFalse(profile.maxDailyLossPercentEnforcedInBacktests)
        assertFalse(profile.maxConcurrentPositionsEnforcedInBacktests)
        assertEquals(BigDecimal("5"), profile.maxDailyLossPercent)
        assertEquals(3, profile.maxConcurrentPositions)
    }

    @Test
    fun `an out-of-range riskPercentPerTrade is rejected with 400`() {
        val auth = register("ta-badrisk@example.com")
        postProfile(auth.accessToken, profileRequest(riskPercentPerTrade = BigDecimal("0"))).andExpect { status { isBadRequest() } }
        postProfile(auth.accessToken, profileRequest(riskPercentPerTrade = BigDecimal("150"))).andExpect { status { isBadRequest() } }
    }

    @Test
    fun `an out-of-range maxDailyLossPercent is rejected with 400`() {
        val auth = register("ta-baddailyloss@example.com")
        postProfile(auth.accessToken, profileRequest(maxDailyLossPercent = BigDecimal("-1"))).andExpect { status { isBadRequest() } }
        postProfile(auth.accessToken, profileRequest(maxDailyLossPercent = BigDecimal("101"))).andExpect { status { isBadRequest() } }
    }

    @Test
    fun `a malformed accountCurrency is rejected with 400`() {
        val auth = register("ta-badcurrency@example.com")
        postProfile(auth.accessToken, profileRequest(accountCurrency = "US")).andExpect { status { isBadRequest() } }
    }

    @Test
    fun `a non-positive maxConcurrentPositions is rejected with 400`() {
        val auth = register("ta-badconcurrent@example.com")
        postProfile(auth.accessToken, profileRequest(maxConcurrentPositions = 0)).andExpect { status { isBadRequest() } }
    }

    @Test
    fun `list only returns this user's own non-archived profiles`() {
        val authA = register("ta-list-a@example.com")
        val authB = register("ta-list-b@example.com")
        createProfile(authA.accessToken, profileRequest(name = "A1"))
        createProfile(authA.accessToken, profileRequest(name = "A2"))
        createProfile(authB.accessToken, profileRequest(name = "B1"))

        val response = mockMvc.get("/api/v1/trading-accounts") {
            header("Authorization", "Bearer ${authA.accessToken}")
        }.andReturn().response
        assertEquals(200, response.status)
        val profiles = objectMapper.readValue(response.contentAsString, Array<TradingAccountProfileResponse>::class.java)
        assertEquals(2, profiles.size)
        assertTrue(profiles.all { it.name.startsWith("A") })
    }

    @Test
    fun `getting another user's profile returns 404, not leaking existence`() {
        val authA = register("ta-get-a@example.com")
        val authB = register("ta-get-b@example.com")
        val profile = createProfile(authA.accessToken)

        mockMvc.get("/api/v1/trading-accounts/${profile.id}") {
            header("Authorization", "Bearer ${authB.accessToken}")
        }.andExpect { status { isNotFound() } }
    }

    @Test
    fun `getting a nonexistent profile id returns 404`() {
        val auth = register("ta-get-missing@example.com")
        mockMvc.get("/api/v1/trading-accounts/${UUID.randomUUID()}") {
            header("Authorization", "Bearer ${auth.accessToken}")
        }.andExpect { status { isNotFound() } }
    }

    @Test
    fun `updating a profile persists the new values and is reflected on a subsequent get`() {
        val auth = register("ta-update@example.com")
        val profile = createProfile(auth.accessToken)

        val updateResponse = mockMvc.put("/api/v1/trading-accounts/${profile.id}") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(profileRequest(name = "Renamed", balance = BigDecimal("25000.00")))
            header("Authorization", "Bearer ${auth.accessToken}")
        }.andReturn().response
        assertEquals(200, updateResponse.status)
        val updated = objectMapper.readValue(updateResponse.contentAsString, TradingAccountProfileResponse::class.java)
        assertEquals("Renamed", updated.name)
        assertEquals(BigDecimal("25000.00"), updated.balance)

        val getResponse = mockMvc.get("/api/v1/trading-accounts/${profile.id}") {
            header("Authorization", "Bearer ${auth.accessToken}")
        }.andReturn().response
        val fetched = objectMapper.readValue(getResponse.contentAsString, TradingAccountProfileResponse::class.java)
        assertEquals("Renamed", fetched.name)
    }

    @Test
    fun `updating another user's profile returns 404`() {
        val authA = register("ta-update-a@example.com")
        val authB = register("ta-update-b@example.com")
        val profile = createProfile(authA.accessToken)

        mockMvc.put("/api/v1/trading-accounts/${profile.id}") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(profileRequest(name = "Hijacked"))
            header("Authorization", "Bearer ${authB.accessToken}")
        }.andExpect { status { isNotFound() } }
    }

    @Test
    fun `archiving (DELETE) removes the profile from the list but it remains individually gettable`() {
        val auth = register("ta-archive@example.com")
        val profile = createProfile(auth.accessToken)

        mockMvc.delete("/api/v1/trading-accounts/${profile.id}") {
            header("Authorization", "Bearer ${auth.accessToken}")
        }.andExpect { status { isNoContent() } }

        val listResponse = mockMvc.get("/api/v1/trading-accounts") {
            header("Authorization", "Bearer ${auth.accessToken}")
        }.andReturn().response
        val profiles = objectMapper.readValue(listResponse.contentAsString, Array<TradingAccountProfileResponse>::class.java)
        assertTrue(profiles.isEmpty())

        val getResponse = mockMvc.get("/api/v1/trading-accounts/${profile.id}") {
            header("Authorization", "Bearer ${auth.accessToken}")
        }.andReturn().response
        assertEquals(200, getResponse.status)
        val fetched = objectMapper.readValue(getResponse.contentAsString, TradingAccountProfileResponse::class.java)
        assertTrue(fetched.archived)
    }

    @Test
    fun `updating an archived profile returns 404 - archive is treated as fully retired, not just list-hidden`() {
        val auth = register("ta-updatearchived@example.com")
        val profile = createProfile(auth.accessToken)

        mockMvc.delete("/api/v1/trading-accounts/${profile.id}") {
            header("Authorization", "Bearer ${auth.accessToken}")
        }.andExpect { status { isNoContent() } }

        mockMvc.put("/api/v1/trading-accounts/${profile.id}") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(profileRequest(name = "Should not apply"))
            header("Authorization", "Bearer ${auth.accessToken}")
        }.andExpect { status { isNotFound() } }
    }

    @Test
    fun `archiving another user's profile returns 404`() {
        val authA = register("ta-archive-a@example.com")
        val authB = register("ta-archive-b@example.com")
        val profile = createProfile(authA.accessToken)

        mockMvc.delete("/api/v1/trading-accounts/${profile.id}") {
            header("Authorization", "Bearer ${authB.accessToken}")
        }.andExpect { status { isNotFound() } }
    }

    @Test
    fun `an unauthenticated request is rejected with 401`() {
        mockMvc.get("/api/v1/trading-accounts").andExpect { status { isUnauthorized() } }
    }
}
