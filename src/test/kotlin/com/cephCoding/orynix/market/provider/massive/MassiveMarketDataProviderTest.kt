package com.cephCoding.orynix.market.provider.massive

import com.cephCoding.orynix.common.exception.MarketDataProviderException
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withServerError
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import java.math.BigDecimal
import java.time.LocalDate

class MassiveMarketDataProviderTest {

    private val properties = MassiveProperties(baseUrl = "https://api.massive.com", apiKey = "test-key")
    private val from: LocalDate = LocalDate.of(2026, 1, 1)
    private val to: LocalDate = LocalDate.of(2026, 1, 2)
    private val expectedUri =
        "https://api.massive.com/v2/aggs/ticker/AAPL/range/1/day/$from/$to?adjusted=true&sort=asc&limit=5000"

    private fun newProvider(): Pair<MassiveMarketDataProvider, MockRestServiceServer> {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        return MassiveMarketDataProvider(builder, properties) to server
    }

    @Test
    fun `maps a successful response into domain candles`() {
        val (provider, server) = newProvider()

        server.expect(requestTo(expectedUri))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("Authorization", "Bearer test-key"))
            .andRespond(
                withSuccess(
                    """
                    {
                      "ticker": "AAPL",
                      "status": "OK",
                      "resultsCount": 1,
                      "results": [
                        { "t": 1577941200000, "o": 74.06, "h": 75.15, "l": 73.7975, "c": 75.0875, "v": 135647456, "vw": 74.6099, "n": 1 }
                      ]
                    }
                    """.trimIndent(),
                    MediaType.APPLICATION_JSON,
                )
            )

        val candles = provider.fetchDailyCandles("AAPL", from, to)

        assert(candles.size == 1)
        val candle = candles.first()
        assert(candle.symbol == "AAPL")
        assert(candle.timeframe.code == "1d")
        assert(candle.open.compareTo(BigDecimal("74.06")) == 0)
        assert(candle.high.compareTo(BigDecimal("75.15")) == 0)
        assert(candle.low.compareTo(BigDecimal("73.7975")) == 0)
        assert(candle.close.compareTo(BigDecimal("75.0875")) == 0)
        assert(candle.volume.compareTo(BigDecimal("135647456")) == 0)
    }

    @Test
    fun `wraps provider failure without leaking request details`() {
        val (provider, server) = newProvider()

        server.expect(requestTo(expectedUri)).andRespond(withServerError())

        val ex = assertThrows(MarketDataProviderException::class.java) {
            provider.fetchDailyCandles("AAPL", from, to)
        }
        assert(ex.message == "Market data provider request failed")
        assert(ex.message?.contains("massive.com") != true)
        assert(ex.message?.contains("test-key") != true)
    }
}
