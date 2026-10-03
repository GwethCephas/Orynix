package com.cephCoding.orynix.user

import com.cephCoding.orynix.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.get

class UserMeTest : AbstractIntegrationTest() {

    @Test
    fun `authenticated request returns current user without sensitive fields`() {
        val auth = register("me-success@example.com")

        mockMvc.get("/api/v1/users/me") {
            header("Authorization", "Bearer ${auth.accessToken}")
        }.andExpect {
            status { isOk() }
            jsonPath("$.email") { value("me-success@example.com") }
            jsonPath("$.passwordHash") { doesNotExist() }
        }
    }

    @Test
    fun `unauthenticated request is rejected`() {
        mockMvc.get("/api/v1/users/me")
            .andExpect { status { isUnauthorized() } }
    }
}
