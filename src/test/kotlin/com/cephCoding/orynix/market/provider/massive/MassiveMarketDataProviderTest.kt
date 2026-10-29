package com.cephCoding.orynix.market.provider.massive

import com.cephCoding.orynix.common.exception.MarketDataProviderException
import com.cephCoding.orynix.market.domain.CandleTimeframe
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

        val candles = provider.fetchCandles("AAPL", from, to, CandleTimeframe.DAILY)

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
            provider.fetchCandles("AAPL", from, to, CandleTimeframe.DAILY)
        }
        assert(ex.message == "Market data provider request failed")
        assert(ex.message?.contains("massive.com") != true)
        assert(ex.message?.contains("test-key") != true)
    }

    @Test
    fun `builds the correct URI for a 15-minute intraday timeframe`() {
        val (provider, server) = newProvider()
        val intradayUri = "https://api.massive.com/v2/aggs/ticker/AAPL/range/15/minute/$from/$to?adjusted=true&sort=asc&limit=5000"

        server.expect(requestTo(intradayUri))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("""{"results":[]}""", MediaType.APPLICATION_JSON))

        provider.fetchCandles("AAPL", from, to, CandleTimeframe.MINUTE_15)
        server.verify()
    }

    @Test
    fun `builds the correct URI for a 1-hour intraday timeframe`() {
        val (provider, server) = newProvider()
        val intradayUri = "https://api.massive.com/v2/aggs/ticker/AAPL/range/1/hour/$from/$to?adjusted=true&sort=asc&limit=5000"

        server.expect(requestTo(intradayUri))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("""{"results":[]}""", MediaType.APPLICATION_JSON))

        provider.fetchCandles("AAPL", from, to, CandleTimeframe.HOUR_1)
        server.verify()
    }

    @Test
    fun `builds the correct URI for a 4-hour intraday timeframe`() {
        val (provider, server) = newProvider()
        val intradayUri = "https://api.massive.com/v2/aggs/ticker/AAPL/range/4/hour/$from/$to?adjusted=true&sort=asc&limit=5000"

        server.expect(requestTo(intradayUri))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("""{"results":[]}""", MediaType.APPLICATION_JSON))

        provider.fetchCandles("AAPL", from, to, CandleTimeframe.HOUR_4)
        server.verify()
    }

    @Test
    fun `follows next_url pagination and combines results from every page`() {
        val (provider, server) = newProvider()
        val nextUri = "https://api.massive.com/v2/aggs/ticker/AAPL/range/1/day/$from/$to?cursor=page2&adjusted=true&sort=asc&limit=5000"

        server.expect(requestTo(expectedUri))
            .andRespond(
                withSuccess(
                    """
                    {
                      "results": [{ "t": 1577941200000, "o": 1.0, "h": 2.0, "l": 0.5, "c": 1.5, "v": 100 }],
                      "next_url": "$nextUri"
                    }
                    """.trimIndent(),
                    MediaType.APPLICATION_JSON,
                )
            )
        server.expect(requestTo(nextUri))
            .andRespond(
                withSuccess(
                    """{ "results": [{ "t": 1578028000000, "o": 1.6, "h": 2.6, "l": 1.1, "c": 2.1, "v": 200 }] }""",
                    MediaType.APPLICATION_JSON,
                )
            )

        val candles = provider.fetchCandles("AAPL", from, to, CandleTimeframe.DAILY)

        assert(candles.size == 2)
        server.verify()
    }

    @Test
    fun `a next_url pointing at an unexpected host is rejected without being followed`() {
        val (provider, server) = newProvider()

        server.expect(requestTo(expectedUri))
            .andRespond(
                withSuccess(
                    """
                    {
                      "results": [{ "t": 1577941200000, "o": 1.0, "h": 2.0, "l": 0.5, "c": 1.5, "v": 100 }],
                      "next_url": "https://evil.example.com/steal?cursor=page2"
                    }
                    """.trimIndent(),
                    MediaType.APPLICATION_JSON,
                )
            )

        assertThrows(MarketDataProviderException::class.java) {
            provider.fetchCandles("AAPL", from, to, CandleTimeframe.DAILY)
        }
        server.verify()
    }

    @Test
    fun `exceeding the maximum page count throws without a further request`() {
        val (provider, server) = newProvider()
        val pageUris = (2..6).map {
            "https://api.massive.com/v2/aggs/ticker/AAPL/range/1/day/$from/$to?cursor=page$it&adjusted=true&sort=asc&limit=5000"
        }
        val allUris = listOf(expectedUri) + pageUris

        // 5 pages are fetched (MAX_PAGES); each still advertises a next page, so the 6th would-be
        // request is never issued - the loop throws before making it.
        allUris.take(5).forEachIndexed { index, uri ->
            server.expect(requestTo(uri))
                .andRespond(
                    withSuccess(
                        """
                        {
                          "results": [{ "t": 1577941200000, "o": 1.0, "h": 2.0, "l": 0.5, "c": 1.5, "v": 100 }],
                          "next_url": "${allUris[index + 1]}"
                        }
                        """.trimIndent(),
                        MediaType.APPLICATION_JSON,
                    )
                )
        }

        assertThrows(MarketDataProviderException::class.java) {
            provider.fetchCandles("AAPL", from, to, CandleTimeframe.DAILY)
        }
        server.verify()
    }
}
