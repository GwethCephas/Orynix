package com.cephCoding.orynix.auth

import com.cephCoding.orynix.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.post

class AuthRegistrationTest : AbstractIntegrationTest() {

    @Test
    fun `successful registration returns tokens and user info`() {
        val response = register("register-success@example.com", "password123")

        assert(response.accessToken.isNotBlank())
        assert(response.refreshToken.isNotBlank())
        assert(response.user.email == "register-success@example.com")
    }

    @Test
    fun `duplicate registration is rejected`() {
        register("duplicate@example.com", "password123")

        mockMvc.post("/api/v1/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                mapOf("email" to "duplicate@example.com", "password" to "password123")
            )
        }.andExpect { status { isConflict() } }
    }

    @Test
    fun `invalid email is rejected`() {
        mockMvc.post("/api/v1/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                mapOf("email" to "not-an-email", "password" to "password123")
            )
        }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `too short password is rejected`() {
        mockMvc.post("/api/v1/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                mapOf("email" to "shortpass@example.com", "password" to "short")
            )
        }.andExpect { status { isBadRequest() } }
    }
}
