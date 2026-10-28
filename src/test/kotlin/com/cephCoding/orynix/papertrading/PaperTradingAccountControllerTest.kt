package com.cephCoding.orynix.papertrading

import com.cephCoding.orynix.papertrading.dto.CreatePaperTradingAccountRequest
import com.cephCoding.orynix.papertrading.dto.PaperTradingAccountResponse
import com.cephCoding.orynix.papertrading.dto.UpdatePaperTradingAccountRequest
import com.cephCoding.orynix.support.AbstractIntegrationTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import java.math.BigDecimal
import java.util.UUID

class PaperTradingAccountControllerTest : AbstractIntegrationTest() {

    private fun createRequest(
        name: String = "Main paper account",
        baseCurrency: String = "USD",
        initialDeposit: BigDecimal = BigDecimal("10000"),
    ) = CreatePaperTradingAccountRequest(name = name, baseCurrency = baseCurrency, initialDeposit = initialDeposit)

    private fun postAccount(token: String, request: CreatePaperTradingAccountRequest) = mockMvc.post("/api/v1/paper-trading/accounts") {
        contentType = MediaType.APPLICATION_JSON
        content = objectMapper.writeValueAsString(request)
        header("Authorization", "Bearer $token")
    }

    private fun createAccount(token: String, request: CreatePaperTradingAccountRequest = createRequest()): PaperTradingAccountResponse {
        val response = postAccount(token, request).andReturn().response
        assertEquals(201, response.status)
        return objectMapper.readValue(response.contentAsString, PaperTradingAccountResponse::class.java)
    }

    @Test
    fun `creating an account seeds cash from initialDeposit and never touches real money`() {
        val auth = register("pt-create@example.com")
        val account = createAccount(auth.accessToken, createRequest(initialDeposit = BigDecimal("25000")))

        assertEquals(BigDecimal("25000.0000"), account.initialDeposit)
        assertEquals(BigDecimal("25000.0000"), account.cash)
        assertEquals("USD", account.baseCurrency)
    }

    @Test
    fun `a non-positive initialDeposit is rejected with 400`() {
        val auth = register("pt-baddeposit@example.com")
        postAccount(auth.accessToken, createRequest(initialDeposit = BigDecimal("0"))).andExpect { status { isBadRequest() } }
        postAccount(auth.accessToken, createRequest(initialDeposit = BigDecimal("-100"))).andExpect { status { isBadRequest() } }
    }

    @Test
    fun `a malformed baseCurrency is rejected with 400`() {
        val auth = register("pt-badcurrency@example.com")
        postAccount(auth.accessToken, createRequest(baseCurrency = "US")).andExpect { status { isBadRequest() } }
    }

    @Test
    fun `list only returns this user's own active accounts`() {
        val authA = register("pt-list-a@example.com")
        val authB = register("pt-list-b@example.com")
        createAccount(authA.accessToken, createRequest(name = "A1"))
        createAccount(authA.accessToken, createRequest(name = "A2"))
        createAccount(authB.accessToken, createRequest(name = "B1"))

        val response = mockMvc.get("/api/v1/paper-trading/accounts") {
            header("Authorization", "Bearer ${authA.accessToken}")
        }.andReturn().response
        val accounts = objectMapper.readValue(response.contentAsString, Array<PaperTradingAccountResponse>::class.java)
        assertEquals(2, accounts.size)
        assertEquals(setOf("A1", "A2"), accounts.map { it.name }.toSet())
    }

    @Test
    fun `renaming an account updates only the name`() {
        val auth = register("pt-rename@example.com")
        val account = createAccount(auth.accessToken)

        val response = mockMvc.put("/api/v1/paper-trading/accounts/${account.id}") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(UpdatePaperTradingAccountRequest(name = "Renamed"))
            header("Authorization", "Bearer ${auth.accessToken}")
        }.andReturn().response
        val updated = objectMapper.readValue(response.contentAsString, PaperTradingAccountResponse::class.java)

        assertEquals("Renamed", updated.name)
        assertEquals(account.cash, updated.cash)
    }

    @Test
    fun `archiving removes an account from the list but a direct get still works`() {
        val auth = register("pt-archive@example.com")
        val account = createAccount(auth.accessToken)

        mockMvc.delete("/api/v1/paper-trading/accounts/${account.id}") {
            header("Authorization", "Bearer ${auth.accessToken}")
        }.andExpect { status { isNoContent() } }

        val listResponse = mockMvc.get("/api/v1/paper-trading/accounts") {
            header("Authorization", "Bearer ${auth.accessToken}")
        }.andReturn().response
        val accounts = objectMapper.readValue(listResponse.contentAsString, Array<PaperTradingAccountResponse>::class.java)
        assertEquals(0, accounts.size)

        mockMvc.get("/api/v1/paper-trading/accounts/${account.id}") {
            header("Authorization", "Bearer ${auth.accessToken}")
        }.andExpect { status { isOk() } }
    }

    @Test
    fun `an unknown or another user's account id returns 404, not 403`() {
        val authA = register("pt-cross-a@example.com")
        val authB = register("pt-cross-b@example.com")
        val accountB = createAccount(authB.accessToken)

        mockMvc.get("/api/v1/paper-trading/accounts/${UUID.randomUUID()}") {
            header("Authorization", "Bearer ${authA.accessToken}")
        }.andExpect { status { isNotFound() } }

        mockMvc.get("/api/v1/paper-trading/accounts/${accountB.id}") {
            header("Authorization", "Bearer ${authA.accessToken}")
        }.andExpect { status { isNotFound() } }
    }

    @Test
    fun `missing authentication is rejected with 401`() {
        mockMvc.get("/api/v1/paper-trading/accounts").andExpect { status { isUnauthorized() } }
    }
}
