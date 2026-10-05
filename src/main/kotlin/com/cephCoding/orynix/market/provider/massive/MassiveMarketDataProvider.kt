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
import java.time.Instant
import java.time.LocalDate

@Component
class MassiveMarketDataProvider(
    restClientBuilder: RestClient.Builder,
    private val massiveProperties: MassiveProperties,
) : MarketDataProvider {

    private val restClient = restClientBuilder.baseUrl(massiveProperties.baseUrl).build()

    override fun fetchDailyCandles(symbol: String, from: LocalDate, to: LocalDate): List<Candle> {
        val response = try {
            restClient.get()
                .uri(
                    "/v2/aggs/ticker/{symbol}/range/1/day/{from}/{to}?adjusted=true&sort=asc&limit=5000",
                    symbol, from, to,
                )
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${massiveProperties.apiKey}")
                .retrieve()
                .body(MassiveAggregatesResponse::class.java)
        } catch (ex: RestClientException) {
            throw MarketDataProviderException("Market data provider request failed")
        }

        return (response?.results ?: emptyList()).map { it.toCandle(symbol) }
    }

    private fun MassiveBar.toCandle(symbol: String) = Candle(
        symbol = symbol,
        timeframe = CandleTimeframe.DAILY,
        timestamp = Instant.ofEpochMilli(t),
        open = o,
        high = h,
        low = l,
        close = c,
        volume = v,
    )
}
