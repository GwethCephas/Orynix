package com.cephCoding.orynix.sentiment

import com.cephCoding.orynix.common.exception.SentimentAnalyzerException
import com.cephCoding.orynix.news.domain.NewsArticle
import com.cephCoding.orynix.sentiment.domain.AnalyzedSentiment
import com.cephCoding.orynix.sentiment.domain.Sentiment
import com.cephCoding.orynix.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.get
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class SentimentControllerTest : AbstractIntegrationTest() {

    @Test
    fun `authenticated request returns sentiment for a news article`() {
        val auth = register("sentiment-success@example.com").accessToken
        fakeNewsProvider.articlesToReturn = listOf(
            NewsArticle(
                id = null, providerArticleId = "sc-1", symbol = "AAPL",
                title = "Apple beats expectations", description = "Strong quarter.",
                source = "Example Wire", url = "https://example.com/sc-1",
                author = "Reporter", publishedAt = Instant.parse("2026-01-02T00:00:00Z"),
            )
        )
        fakeSentimentAnalyzer.sentimentToReturn = AnalyzedSentiment(Sentiment.POSITIVE, BigDecimal("0.7000"), BigDecimal("0.9500"))

        val articleIdResponse = mockMvc.get("/api/v1/news") { param("symbol", "AAPL"); header("Authorization", "Bearer $auth") }
            .andReturn().response.contentAsString
        val articleId = objectMapper.readTree(articleIdResponse).get("articles").get(0).get("id").asString()

        mockMvc.get("/api/v1/news/$articleId/sentiment") {
            header("Authorization", "Bearer $auth")
        }.andExpect {
            status { isOk() }
            jsonPath("$.sentiment") { value("POSITIVE") }
            jsonPath("$.score") { value(0.7000) }
            jsonPath("$.analyzer") { value("fake-analyzer") }
        }
    }

    @Test
    fun `malformed article id is rejected with 400`() {
        val auth = register("sentiment-badid@example.com").accessToken

        mockMvc.get("/api/v1/news/not-a-uuid/sentiment") {
            header("Authorization", "Bearer $auth")
        }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `well-formed id with no matching article returns 404`() {
        val auth = register("sentiment-notfound@example.com").accessToken

        mockMvc.get("/api/v1/news/${UUID.randomUUID()}/sentiment") {
            header("Authorization", "Bearer $auth")
        }.andExpect { status { isNotFound() } }
    }

    @Test
    fun `analyzer failure surfaces as 502 without leaking details`() {
        val auth = register("sentiment-analyzerfail@example.com").accessToken
        fakeNewsProvider.articlesToReturn = listOf(
            NewsArticle(
                id = null, providerArticleId = "sc-fail", symbol = "AAPL",
                title = "Title", description = null,
                source = null, url = null, author = null,
                publishedAt = Instant.parse("2026-01-02T00:00:00Z"),
            )
        )
        val response = mockMvc.get("/api/v1/news") { param("symbol", "AAPL"); header("Authorization", "Bearer $auth") }
            .andReturn().response.contentAsString
        val articleId = objectMapper.readTree(response).get("articles").get(0).get("id").asString()

        fakeSentimentAnalyzer.failure = SentimentAnalyzerException("Sentiment analyzer request failed")

        mockMvc.get("/api/v1/news/$articleId/sentiment") {
            header("Authorization", "Bearer $auth")
        }.andExpect {
            status { isBadGateway() }
            jsonPath("$.message") { value("Sentiment analyzer request failed") }
        }
    }

    @Test
    fun `unauthenticated single-article request is rejected`() {
        mockMvc.get("/api/v1/news/${UUID.randomUUID()}/sentiment")
            .andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `aggregate success response includes counts and overall sentiment`() {
        val auth = register("sentiment-aggregate@example.com").accessToken
        fakeNewsProvider.articlesToReturn = listOf(
            NewsArticle(
                id = null, providerArticleId = "agg-1", symbol = "MSFT",
                title = "Title", description = null, source = null, url = null, author = null,
                publishedAt = Instant.parse("2026-01-02T00:00:00Z"),
            )
        )
        fakeSentimentAnalyzer.sentimentToReturn = AnalyzedSentiment(Sentiment.POSITIVE, BigDecimal("0.5000"), BigDecimal("0.8000"))

        mockMvc.get("/api/v1/news/sentiment") {
            param("symbol", "MSFT")
            header("Authorization", "Bearer $auth")
        }.andExpect {
            status { isOk() }
            jsonPath("$.symbol") { value("MSFT") }
            jsonPath("$.articleCount") { value(1) }
            jsonPath("$.positiveCount") { value(1) }
            jsonPath("$.overallSentiment") { value("POSITIVE") }
        }
    }

    @Test
    fun `aggregate on an empty symbol returns the pinned zero state`() {
        val auth = register("sentiment-aggregateempty@example.com").accessToken
        fakeNewsProvider.articlesToReturn = emptyList()

        mockMvc.get("/api/v1/news/sentiment") {
            param("symbol", "ZZZZ")
            header("Authorization", "Bearer $auth")
        }.andExpect {
            status { isOk() }
            jsonPath("$.articleCount") { value(0) }
            jsonPath("$.overallSentiment") { value("NEUTRAL") }
        }
    }

    @Test
    fun `aggregate with invalid symbol is rejected with 400`() {
        val auth = register("sentiment-aggregatebadsymbol@example.com").accessToken

        mockMvc.get("/api/v1/news/sentiment") {
            param("symbol", "this-is-not-a-ticker-123")
            header("Authorization", "Bearer $auth")
        }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `aggregate with malformed limit is rejected with 400`() {
        val auth = register("sentiment-aggregatebadlimit@example.com").accessToken

        mockMvc.get("/api/v1/news/sentiment") {
            param("symbol", "AAPL")
            param("limit", "abc")
            header("Authorization", "Bearer $auth")
        }.andExpect { status { isBadRequest() } }
    }
}
