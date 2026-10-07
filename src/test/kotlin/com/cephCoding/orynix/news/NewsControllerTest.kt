package com.cephCoding.orynix.news

import com.cephCoding.orynix.common.exception.NewsDataProviderException
import com.cephCoding.orynix.news.domain.NewsArticle
import com.cephCoding.orynix.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.get
import java.time.Instant

class NewsControllerTest : AbstractIntegrationTest() {

    @Test
    fun `authenticated request returns news articles`() {
        val auth = register("news-success@example.com")
        fakeNewsProvider.articlesToReturn = listOf(
            NewsArticle(
                id = null,
                providerArticleId = "art-1",
                symbol = "AAPL",
                title = "Apple unveils new product",
                description = "A description.",
                source = "Example Wire",
                url = "https://example.com/art-1",
                author = "Jane Reporter",
                publishedAt = Instant.parse("2026-01-02T00:00:00Z"),
            )
        )

        mockMvc.get("/api/v1/news") {
            param("symbol", "AAPL")
            header("Authorization", "Bearer ${auth.accessToken}")
        }.andExpect {
            status { isOk() }
            jsonPath("$.symbol") { value("AAPL") }
            jsonPath("$.articles[0].title") { value("Apple unveils new product") }
            jsonPath("$.articles[0].source") { value("Example Wire") }
        }
    }

    @Test
    fun `invalid symbol is rejected with 400`() {
        val auth = register("news-badsymbol@example.com")

        mockMvc.get("/api/v1/news") {
            param("symbol", "this-is-not-a-ticker-123")
            header("Authorization", "Bearer ${auth.accessToken}")
        }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `invalid limit is rejected with 400`() {
        val auth = register("news-badlimit@example.com")

        mockMvc.get("/api/v1/news") {
            param("symbol", "AAPL")
            param("limit", "0")
            header("Authorization", "Bearer ${auth.accessToken}")
        }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `empty results return 200 with an empty articles array`() {
        val auth = register("news-empty@example.com")
        fakeNewsProvider.articlesToReturn = emptyList()

        mockMvc.get("/api/v1/news") {
            param("symbol", "ZZZZ")
            header("Authorization", "Bearer ${auth.accessToken}")
        }.andExpect {
            status { isOk() }
            jsonPath("$.articles.length()") { value(0) }
        }
    }

    @Test
    fun `provider failure surfaces as 502 without leaking details`() {
        val auth = register("news-providerfail@example.com")
        fakeNewsProvider.failure = NewsDataProviderException("News data provider request failed")

        mockMvc.get("/api/v1/news") {
            param("symbol", "AAPL")
            header("Authorization", "Bearer ${auth.accessToken}")
        }.andExpect {
            status { isBadGateway() }
            jsonPath("$.message") { value("News data provider request failed") }
        }
    }

    @Test
    fun `unauthenticated request is rejected`() {
        mockMvc.get("/api/v1/news") {
            param("symbol", "AAPL")
        }.andExpect { status { isUnauthorized() } }
    }
}
