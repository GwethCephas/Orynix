package com.cephCoding.orynix.fundamentals

import com.cephCoding.orynix.common.exception.CompanyNotFoundException
import com.cephCoding.orynix.common.exception.FundamentalDataProviderException
import com.cephCoding.orynix.fundamentals.domain.CompanyProfile
import com.cephCoding.orynix.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.get
import java.math.BigDecimal

class FundamentalsControllerTest : AbstractIntegrationTest() {

    @Test
    fun `authenticated request returns the company profile`() {
        val auth = register("fundamentals-success@example.com")
        fakeFundamentalDataProvider.companyProfileToReturn = CompanyProfile(
            name = "Apple Inc.",
            exchange = "XNAS",
            industry = "ELECTRONIC COMPUTERS",
            marketCapitalization = BigDecimal("4858256580200"),
        )

        mockMvc.get("/api/v1/fundamentals") {
            param("symbol", "AAPL")
            header("Authorization", "Bearer ${auth.accessToken}")
        }.andExpect {
            status { isOk() }
            jsonPath("$.symbol") { value("AAPL") }
            jsonPath("$.company.name") { value("Apple Inc.") }
            jsonPath("$.company.exchange") { value("XNAS") }
        }
    }

    @Test
    fun `invalid symbol is rejected with 400`() {
        val auth = register("fundamentals-badsymbol@example.com")

        mockMvc.get("/api/v1/fundamentals") {
            param("symbol", "this-is-not-a-ticker-123")
            header("Authorization", "Bearer ${auth.accessToken}")
        }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `unknown company is rejected with 404`() {
        val auth = register("fundamentals-notfound@example.com")
        fakeFundamentalDataProvider.failure = CompanyNotFoundException("ZZZZZQQQQQ")

        mockMvc.get("/api/v1/fundamentals") {
            param("symbol", "ZZZZZQQQQQ")
            header("Authorization", "Bearer ${auth.accessToken}")
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.message") { value("Company not found: ZZZZZQQQQQ") }
        }
    }

    @Test
    fun `provider failure surfaces as 502 without leaking details`() {
        val auth = register("fundamentals-providerfail@example.com")
        fakeFundamentalDataProvider.failure = FundamentalDataProviderException("Fundamental data provider request failed")

        mockMvc.get("/api/v1/fundamentals") {
            param("symbol", "AAPL")
            header("Authorization", "Bearer ${auth.accessToken}")
        }.andExpect {
            status { isBadGateway() }
            jsonPath("$.message") { value("Fundamental data provider request failed") }
        }
    }

    @Test
    fun `unauthenticated request is rejected`() {
        mockMvc.get("/api/v1/fundamentals") {
            param("symbol", "AAPL")
        }.andExpect { status { isUnauthorized() } }
    }
}
