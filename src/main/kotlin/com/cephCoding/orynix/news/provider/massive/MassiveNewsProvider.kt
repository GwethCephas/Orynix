package com.cephCoding.orynix.news.provider.massive

import com.cephCoding.orynix.common.exception.NewsDataProviderException
import com.cephCoding.orynix.market.provider.massive.MassiveProperties
import com.cephCoding.orynix.news.domain.NewsArticle
import com.cephCoding.orynix.news.provider.NewsDataProvider
import com.cephCoding.orynix.news.provider.massive.dto.MassiveNewsArticle
import com.cephCoding.orynix.news.provider.massive.dto.MassiveNewsResponse
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import java.time.Instant
import java.time.format.DateTimeParseException

@Component
class MassiveNewsProvider(
    restClientBuilder: RestClient.Builder,
    private val massiveProperties: MassiveProperties,
) : NewsDataProvider {

    private val restClient = restClientBuilder.baseUrl(massiveProperties.baseUrl).build()

    override fun fetchNews(symbol: String, limit: Int): List<NewsArticle> {
        val response = try {
            restClient.get()
                .uri(
                    "/v2/reference/news?ticker={symbol}&limit={limit}&order=desc&sort=published_utc",
                    symbol, limit,
                )
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${massiveProperties.apiKey}")
                .retrieve()
                .body(MassiveNewsResponse::class.java)
        } catch (ex: RestClientException) {
            throw NewsDataProviderException("News data provider request failed")
        }

        return (response?.results ?: emptyList()).mapNotNull { it.toNewsArticle(symbol) }
    }

    /**
     * Massive's article `id`, `title`, and `published_utc` are treated as structurally required —
     * an article missing any of them is skipped rather than persisted with a fabricated value.
     */
    private fun MassiveNewsArticle.toNewsArticle(symbol: String): NewsArticle? {
        val providerArticleId = id ?: return null
        val articleTitle = title ?: return null
        val publishedAt = publishedUtc?.let {
            try {
                Instant.parse(it)
            } catch (ex: DateTimeParseException) {
                null
            }
        } ?: return null

        return NewsArticle(
            id = null,
            providerArticleId = providerArticleId,
            symbol = symbol,
            title = articleTitle,
            description = description,
            source = publisher?.name,
            url = articleUrl,
            author = author,
            publishedAt = publishedAt,
        )
    }
}
