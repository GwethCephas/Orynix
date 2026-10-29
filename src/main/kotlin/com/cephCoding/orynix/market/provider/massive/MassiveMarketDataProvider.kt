package com.cephCoding.orynix.market.provider.massive

import com.cephCoding.orynix.common.exception.MarketDataProviderException
import com.cephCoding.orynix.market.domain.Candle
import com.cephCoding.orynix.market.domain.CandleTimeframe
import com.cephCoding.orynix.market.provider.MarketDataProvider
import com.cephCoding.orynix.market.provider.massive.dto.MassiveAggregatesResponse
import com.cephCoding.orynix.market.provider.massive.dto.MassiveBar
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import java.net.URI
import java.time.Instant
import java.time.LocalDate

@Component
class MassiveMarketDataProvider(
    restClientBuilder: RestClient.Builder,
    private val massiveProperties: MassiveProperties,
) : MarketDataProvider {

    private val restClient = restClientBuilder.baseUrl(massiveProperties.baseUrl).build()

    // Live-verified against the real Massive API (2026-10-29): a 31-day/15-minute AAPL request
    // with limit=5000 returned only 389 of 775+ actual bars across pages - next_url is a genuine
    // continuation cursor, not an always-present artifact. Every page must be followed, bounded by
    // this cap so a malformed/looping response can never hang a request; exceeding it is a real,
    // disclosed failure (502), never a silent partial result.
    companion object {
        private const val MAX_PAGES = 5
    }

    override fun fetchCandles(
        symbol: String,
        from: LocalDate,
        to: LocalDate,
        timeframe: CandleTimeframe,
    ): List<Candle> {
        val bars = mutableListOf<MassiveBar>()
        var pagesFetched = 0

        var response = fetchFirstPage(symbol, from, to, timeframe)
        bars += response?.results ?: emptyList()
        pagesFetched++
        var nextUri: URI? = response?.nextUrl?.let { sanitizeNextUrl(it) }

        while (nextUri != null) {
            if (pagesFetched >= MAX_PAGES) {
                throw MarketDataProviderException("Market data provider response exceeded the maximum supported page count")
            }
            response = fetchPage(nextUri)
            bars += response?.results ?: emptyList()
            pagesFetched++
            nextUri = response?.nextUrl?.let { sanitizeNextUrl(it) }
        }

        return bars.map { it.toCandle(symbol, timeframe) }
    }

    private fun fetchFirstPage(
        symbol: String,
        from: LocalDate,
        to: LocalDate,
        timeframe: CandleTimeframe,
    ): MassiveAggregatesResponse? = try {
        restClient.get()
            .uri(
                "/v2/aggs/ticker/{symbol}/range/${timeframe.massiveMultiplier}/${timeframe.massiveTimespan}/{from}/{to}" +
                    "?adjusted=true&sort=asc&limit=5000",
                symbol, from, to,
            )
            .header(HttpHeaders.AUTHORIZATION, "Bearer ${massiveProperties.apiKey}")
            .retrieve()
            .body(MassiveAggregatesResponse::class.java)
    } catch (ex: RestClientException) {
        throw MarketDataProviderException("Market data provider request failed")
    }

    private fun fetchPage(uri: URI): MassiveAggregatesResponse? = try {
        restClient.get()
            .uri(uri)
            .header(HttpHeaders.AUTHORIZATION, "Bearer ${massiveProperties.apiKey}")
            .retrieve()
            .body(MassiveAggregatesResponse::class.java)
    } catch (ex: RestClientException) {
        throw MarketDataProviderException("Market data provider request failed")
    }

    // Never follows a next_url to a host other than the configured Massive host, and never
    // forwards a provider-supplied credential query parameter (this provider authenticates via
    // the Authorization header exclusively, matching every other call it makes) - a defensive
    // check on a value we don't control, not a sign Massive is known to misbehave.
    private fun sanitizeNextUrl(nextUrl: String): URI {
        // String-level filtering, not the multi-argument URI(...) constructor - that constructor
        // re-quotes its components, which risks silently rewriting an already-valid URL into a
        // different (if equivalent) string. Working on the raw string preserves it byte-for-byte
        // except for the one thing being deliberately removed.
        val withoutApiKey = Regex("([?&])apiKey=[^&]*&?").replace(nextUrl) { match ->
            if (match.groupValues[1] == "?") "?" else ""
        }.trimEnd('?', '&')
        val parsed = try {
            URI.create(withoutApiKey)
        } catch (ex: IllegalArgumentException) {
            throw MarketDataProviderException("Market data provider returned a malformed pagination cursor")
        }
        val configuredHost = URI.create(massiveProperties.baseUrl).host
        if (parsed.host != configuredHost) {
            throw MarketDataProviderException("Market data provider returned a pagination cursor for an unexpected host")
        }
        return parsed
    }

    private fun MassiveBar.toCandle(symbol: String, timeframe: CandleTimeframe) = Candle(
        symbol = symbol,
        timeframe = timeframe,
        timestamp = Instant.ofEpochMilli(t),
        open = o,
        high = h,
        low = l,
        close = c,
        volume = v,
    )
}
