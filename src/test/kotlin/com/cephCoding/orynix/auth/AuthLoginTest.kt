package com.cephCoding.orynix.auth

import com.cephCoding.orynix.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.post

class AuthLoginTest : AbstractIntegrationTest() {

    @Test
    fun `successful login returns tokens`() {
        register("login-success@example.com", "password123")

        val result = mockMvc.post("/api/v1/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                mapOf("email" to "login-success@example.com", "password" to "password123")
            )
        }.andExpect { status { isOk() } }.andReturn()

        val response = objectMapper.readValue(
            result.response.contentAsString,
            com.cephCoding.orynix.auth.dto.AuthResponse::class.java,
        )
        assert(response.accessToken.isNotBlank())
        assert(response.refreshToken.isNotBlank())
    }

    @Test
    fun `wrong password is rejected`() {
        register("login-wrongpass@example.com", "password123")

        mockMvc.post("/api/v1/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                mapOf("email" to "login-wrongpass@example.com", "password" to "wrong-password")
            )
        }.andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `unknown email is rejected`() {
        mockMvc.post("/api/v1/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                mapOf("email" to "no-such-user@example.com", "password" to "password123")
            )
        }.andExpect { status { isUnauthorized() } }
    }
}
