package com.cephCoding.orynix.auth

import com.cephCoding.orynix.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.post

class AuthLogoutTest : AbstractIntegrationTest() {

    @Test
    fun `logout revokes the refresh token`() {
        val auth = register("logout-success@example.com")

        logoutRequest(auth.refreshToken, auth.accessToken).andExpect { status { isNoContent() } }

        refreshRequest(auth.refreshToken).andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `logout without authentication is rejected`() {
        mockMvc.post("/api/v1/auth/logout") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("refreshToken" to "irrelevant"))
        }.andExpect { status { isUnauthorized() } }
    }
}
