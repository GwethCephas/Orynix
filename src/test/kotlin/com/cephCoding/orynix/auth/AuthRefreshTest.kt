package com.cephCoding.orynix.auth

import com.cephCoding.orynix.auth.dto.AuthResponse
import com.cephCoding.orynix.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test

class AuthRefreshTest : AbstractIntegrationTest() {

    @Test
    fun `valid refresh token rotates and returns a new pair`() {
        val initial = register("refresh-success@example.com")

        val result = refreshRequest(initial.refreshToken)
            .andExpect { status { isOk() } }
            .andReturn()

        val rotated = objectMapper.readValue(result.response.contentAsString, AuthResponse::class.java)
        assert(rotated.refreshToken != initial.refreshToken)
        assert(rotated.accessToken.isNotBlank())
    }

    @Test
    fun `reusing a rotated refresh token is rejected`() {
        val initial = register("refresh-reuse@example.com")

        refreshRequest(initial.refreshToken).andExpect { status { isOk() } }
        refreshRequest(initial.refreshToken).andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `unknown refresh token is rejected`() {
        refreshRequest("not-a-real-token").andExpect { status { isUnauthorized() } }
    }
}
