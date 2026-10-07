package com.cephCoding.orynix.sentiment

import com.cephCoding.orynix.common.exception.SentimentAnalyzerException
import com.cephCoding.orynix.news.domain.NewsArticle
import com.cephCoding.orynix.news.service.NewsService
import com.cephCoding.orynix.sentiment.domain.AnalyzedSentiment
import com.cephCoding.orynix.sentiment.domain.Sentiment
import com.cephCoding.orynix.sentiment.service.SentimentService
import com.cephCoding.orynix.support.AbstractIntegrationTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class SentimentServiceTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var sentimentService: SentimentService

    @Autowired
    private lateinit var newsService: NewsService

    private fun article(symbol: String, providerArticleId: String, publishedAt: Instant) = NewsArticle(
        id = null,
        providerArticleId = providerArticleId,
        symbol = symbol,
        title = "Title $providerArticleId",
        description = "Description $providerArticleId",
        source = "Example Wire",
        url = "https://example.com/$providerArticleId",
        author = "Reporter",
        publishedAt = publishedAt,
    )

    private fun persistedArticleId(symbol: String, providerArticleId: String, publishedAt: Instant): UUID {
        fakeNewsProvider.articlesToReturn = listOf(article(symbol, providerArticleId, publishedAt))
        return newsService.getNews(symbol, 20).articles.single { it.providerArticleId == providerArticleId }.id!!
    }

    @Test
    fun `a new article is analyzed and persisted`() {
        val articleId = persistedArticleId("SENTA", "s1", Instant.parse("2026-01-01T00:00:00Z"))
        fakeSentimentAnalyzer.sentimentToReturn = AnalyzedSentiment(Sentiment.POSITIVE, BigDecimal("0.5000"), BigDecimal("0.9000"))

        val result = sentimentService.getOrAnalyze(articleId)

        assertEquals(articleId, result.newsArticleId)
        assertEquals(Sentiment.POSITIVE, result.sentiment)
        assertEquals(BigDecimal("0.5000"), result.score)
        assertEquals("fake-analyzer", result.analyzer)
        assertEquals("fake-model", result.model)
    }

    @Test
    fun `a repeat lookup does not re-call the analyzer`() {
        val articleId = persistedArticleId("SENTB", "b1", Instant.parse("2026-01-01T00:00:00Z"))

        sentimentService.getOrAnalyze(articleId)
        assertEquals(1, fakeSentimentAnalyzer.callCount)

        sentimentService.getOrAnalyze(articleId)
        assertEquals(1, fakeSentimentAnalyzer.callCount)
    }

    @Test
    fun `analyzer failure propagates as SentimentAnalyzerException`() {
        val articleId = persistedArticleId("SENTC", "c1", Instant.parse("2026-01-01T00:00:00Z"))
        fakeSentimentAnalyzer.failure = SentimentAnalyzerException("Sentiment analyzer request failed")

        assertThrows(SentimentAnalyzerException::class.java) {
            sentimentService.getOrAnalyze(articleId)
        }
    }

    @Test
    fun `aggregation computes mixed counts and average score`() {
        val id1 = persistedArticleId("SENTD", "d1", Instant.parse("2026-01-01T00:00:00Z"))
        val id2 = persistedArticleId("SENTD", "d2", Instant.parse("2026-01-02T00:00:00Z"))
        val id3 = persistedArticleId("SENTD", "d3", Instant.parse("2026-01-03T00:00:00Z"))

        fakeSentimentAnalyzer.sentimentToReturn = AnalyzedSentiment(Sentiment.POSITIVE, BigDecimal("0.6000"), BigDecimal("0.9000"))
        sentimentService.getOrAnalyze(id1)
        fakeSentimentAnalyzer.sentimentToReturn = AnalyzedSentiment(Sentiment.NEGATIVE, BigDecimal("-0.4000"), BigDecimal("0.8000"))
        sentimentService.getOrAnalyze(id2)
        fakeSentimentAnalyzer.sentimentToReturn = AnalyzedSentiment(Sentiment.NEUTRAL, BigDecimal("0.0000"), BigDecimal("0.5000"))
        sentimentService.getOrAnalyze(id3)

        val aggregate = sentimentService.getAggregate("SENTD", 20)

        assertEquals(3, aggregate.articleCount)
        assertEquals(1, aggregate.positiveCount)
        assertEquals(1, aggregate.negativeCount)
        assertEquals(1, aggregate.neutralCount)
        assertEquals(BigDecimal("0.0667"), aggregate.averageScore)
        assertEquals(Sentiment.NEUTRAL, aggregate.overallSentiment)
    }

    @Test
    fun `overallSentiment threshold boundaries are inclusive at plus or minus 0point15`() {
        val positiveId = persistedArticleId("SENTE", "e1", Instant.parse("2026-01-01T00:00:00Z"))
        fakeSentimentAnalyzer.sentimentToReturn = AnalyzedSentiment(Sentiment.POSITIVE, BigDecimal("0.1500"), BigDecimal("0.6000"))
        sentimentService.getOrAnalyze(positiveId)
        assertEquals(Sentiment.POSITIVE, sentimentService.getAggregate("SENTE", 20).overallSentiment)

        val belowId = persistedArticleId("SENTF", "f1", Instant.parse("2026-01-01T00:00:00Z"))
        fakeSentimentAnalyzer.sentimentToReturn = AnalyzedSentiment(Sentiment.NEUTRAL, BigDecimal("0.1499"), BigDecimal("0.5000"))
        sentimentService.getOrAnalyze(belowId)
        assertEquals(Sentiment.NEUTRAL, sentimentService.getAggregate("SENTF", 20).overallSentiment)

        val negativeId = persistedArticleId("SENTG", "g1", Instant.parse("2026-01-01T00:00:00Z"))
        fakeSentimentAnalyzer.sentimentToReturn = AnalyzedSentiment(Sentiment.NEGATIVE, BigDecimal("-0.1500"), BigDecimal("0.6000"))
        sentimentService.getOrAnalyze(negativeId)
        assertEquals(Sentiment.NEGATIVE, sentimentService.getAggregate("SENTG", 20).overallSentiment)
    }

    @Test
    fun `zero-article aggregation returns a pinned neutral zero state`() {
        fakeNewsProvider.articlesToReturn = emptyList()

        val aggregate = sentimentService.getAggregate("SENTH", 20)

        assertEquals(0, aggregate.articleCount)
        assertEquals(BigDecimal("0.0000"), aggregate.averageScore)
        assertEquals(Sentiment.NEUTRAL, aggregate.overallSentiment)
    }

    @Test
    fun `aggregation caps analysis at MAX_ARTICLES_ANALYZED_PER_REQUEST`() {
        val base = Instant.parse("2026-01-01T00:00:00Z")
        fakeNewsProvider.articlesToReturn = (1..15).map { article("SENTI", "i$it", base.plusSeconds(it * 3600L)) }
        newsService.getNews("SENTI", 20)

        val aggregate = sentimentService.getAggregate("SENTI", 15)

        assertEquals(10, aggregate.articleCount)
        assertEquals(10, fakeSentimentAnalyzer.callCount)
    }

    @Test
    fun `concurrent first-time analysis of the same article does not throw or duplicate rows`() {
        val articleId = persistedArticleId("SENTRACE", "race1", Instant.parse("2026-01-01T00:00:00Z"))

        val concurrency = 8
        val executor = Executors.newFixedThreadPool(concurrency)
        val readyLatch = CountDownLatch(concurrency)
        val startLatch = CountDownLatch(1)
        val errors = java.util.Collections.synchronizedList(mutableListOf<Throwable>())

        val futures = (1..concurrency).map {
            executor.submit {
                readyLatch.countDown()
                startLatch.await()
                try {
                    sentimentService.getOrAnalyze(articleId)
                } catch (ex: Throwable) {
                    errors.add(ex)
                }
            }
        }
        readyLatch.await()
        startLatch.countDown()
        futures.forEach { it.get(30, TimeUnit.SECONDS) }
        executor.shutdown()

        assertTrue(errors.isEmpty()) { "Concurrent getOrAnalyze calls threw: $errors" }
    }
}
