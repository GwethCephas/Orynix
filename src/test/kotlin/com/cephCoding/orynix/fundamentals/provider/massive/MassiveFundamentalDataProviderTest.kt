package com.cephCoding.orynix.fundamentals.provider.massive

import com.cephCoding.orynix.common.exception.CompanyNotFoundException
import com.cephCoding.orynix.common.exception.FundamentalDataProviderException
import com.cephCoding.orynix.market.provider.massive.MassiveProperties
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withServerError
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import java.math.BigDecimal

class MassiveFundamentalDataProviderTest {

    private val properties = MassiveProperties(baseUrl = "https://api.massive.com", apiKey = "test-key")
    private val expectedUri = "https://api.massive.com/v3/reference/tickers/AAPL"

    private fun newProvider(): Pair<MassiveFundamentalDataProvider, MockRestServiceServer> {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        return MassiveFundamentalDataProvider(builder, properties) to server
    }

    @Test
    fun `maps a successful response into a company profile`() {
        val (provider, server) = newProvider()

        server.expect(requestTo(expectedUri))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("Authorization", "Bearer test-key"))
            .andRespond(
                withSuccess(
                    """
                    {
                      "request_id": "abc123",
                      "status": "OK",
                      "results": {
                        "ticker": "AAPL",
                        "name": "Apple Inc.",
                        "market": "stocks",
                        "locale": "us",
                        "primary_exchange": "XNAS",
                        "type": "CS",
                        "active": true,
                        "sic_code": "3571",
                        "sic_description": "ELECTRONIC COMPUTERS",
                        "market_cap": 4.8582565802e+12
                      }
                    }
                    """.trimIndent(),
                    MediaType.APPLICATION_JSON,
                )
            )

        val profile = provider.fetchCompanyProfile("AAPL")

        assert(profile.name == "Apple Inc.")
        assert(profile.exchange == "XNAS")
        assert(profile.industry == "ELECTRONIC COMPUTERS")
        assert(profile.marketCapitalization?.compareTo(BigDecimal("4858256580200")) == 0)
    }

    @Test
    fun `maps a 404 ticker-not-found response into CompanyNotFoundException`() {
        val (provider, server) = newProvider()

        server.expect(requestTo(expectedUri))
            .andRespond(
                withStatus(HttpStatus.NOT_FOUND)
                    .body("""{"status":"NOT_FOUND","request_id":"abc123","message":"Ticker not found."}""")
                    .contentType(MediaType.APPLICATION_JSON)
            )

        val ex = assertThrows(CompanyNotFoundException::class.java) {
            provider.fetchCompanyProfile("AAPL")
        }
        assert(ex.message == "Company not found: AAPL")
        assert(ex.message?.contains("massive.com") != true)
        assert(ex.message?.contains("test-key") != true)
    }

    @Test
    fun `wraps a generic server error into FundamentalDataProviderException without leaking request details`() {
        val (provider, server) = newProvider()

        server.expect(requestTo(expectedUri)).andRespond(withServerError())

        val ex = assertThrows(FundamentalDataProviderException::class.java) {
            provider.fetchCompanyProfile("AAPL")
        }
        assert(ex.message == "Fundamental data provider request failed")
        assert(ex.message?.contains("massive.com") != true)
        assert(ex.message?.contains("test-key") != true)
    }
}
