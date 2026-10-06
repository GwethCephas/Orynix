package com.cephCoding.orynix.fundamentals.provider.massive

import com.cephCoding.orynix.common.exception.CompanyNotFoundException
import com.cephCoding.orynix.common.exception.FundamentalDataProviderException
import com.cephCoding.orynix.fundamentals.domain.CompanyProfile
import com.cephCoding.orynix.fundamentals.provider.FundamentalDataProvider
import com.cephCoding.orynix.fundamentals.provider.massive.dto.MassiveTickerDetails
import com.cephCoding.orynix.fundamentals.provider.massive.dto.MassiveTickerDetailsResponse
import com.cephCoding.orynix.market.provider.massive.MassiveProperties
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException

@Component
class MassiveFundamentalDataProvider(
    restClientBuilder: RestClient.Builder,
    private val massiveProperties: MassiveProperties,
) : FundamentalDataProvider {

    private val restClient = restClientBuilder.baseUrl(massiveProperties.baseUrl).build()

    override fun fetchCompanyProfile(symbol: String): CompanyProfile {
        val response = try {
            restClient.get()
                .uri("/v3/reference/tickers/{symbol}", symbol)
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${massiveProperties.apiKey}")
                .retrieve()
                .body(MassiveTickerDetailsResponse::class.java)
        } catch (ex: HttpClientErrorException.NotFound) {
            throw CompanyNotFoundException(symbol)
        } catch (ex: RestClientException) {
            throw FundamentalDataProviderException("Fundamental data provider request failed")
        }

        val results = response?.results
            ?: throw FundamentalDataProviderException("Fundamental data provider request failed")

        return results.toCompanyProfile()
    }

    private fun MassiveTickerDetails.toCompanyProfile() = CompanyProfile(
        name = name ?: throw FundamentalDataProviderException("Fundamental data provider request failed"),
        exchange = primaryExchange,
        industry = sicDescription,
        marketCapitalization = marketCap,
    )
}
