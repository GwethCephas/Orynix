package com.cephCoding.orynix.support

import com.cephCoding.orynix.auth.dto.AuthResponse
import org.junit.jupiter.api.AfterEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import tools.jackson.databind.ObjectMapper

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class, MarketDataTestConfiguration::class)
abstract class AbstractIntegrationTest {

    @Autowired
    protected lateinit var mockMvc: MockMvc

    @Autowired
    protected lateinit var objectMapper: ObjectMapper

    @Autowired
    protected lateinit var fakeMarketDataProvider: FakeMarketDataProvider

    @AfterEach
    fun resetFakeMarketDataProvider() {
        fakeMarketDataProvider.reset()
    }

    protected fun register(email: String, password: String = "password123"): AuthResponse {
        val response = mockMvc.post("/api/v1/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("email" to email, "password" to password))
        }.andReturn().response
        return objectMapper.readValue(response.contentAsString, AuthResponse::class.java)
    }

    protected fun refreshRequest(refreshToken: String) = mockMvc.post("/api/v1/auth/refresh") {
        contentType = MediaType.APPLICATION_JSON
        content = objectMapper.writeValueAsString(mapOf("refreshToken" to refreshToken))
    }

    protected fun logoutRequest(refreshToken: String, accessToken: String? = null) = mockMvc.post("/api/v1/auth/logout") {
        contentType = MediaType.APPLICATION_JSON
        content = objectMapper.writeValueAsString(mapOf("refreshToken" to refreshToken))
        accessToken?.let { header("Authorization", "Bearer $it") }
    }
}
