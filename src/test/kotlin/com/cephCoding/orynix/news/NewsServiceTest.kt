package com.cephCoding.orynix.news

import com.cephCoding.orynix.common.exception.InvalidNewsLimitException
import com.cephCoding.orynix.common.exception.InvalidSymbolException
import com.cephCoding.orynix.news.domain.NewsArticle
import com.cephCoding.orynix.news.service.NewsService
import com.cephCoding.orynix.support.AbstractIntegrationTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class NewsServiceTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var newsService: NewsService

    private fun article(symbol: String, providerArticleId: String, publishedAt: Instant, title: String = "Title $providerArticleId") =
        NewsArticle(
            id = null,
            providerArticleId = providerArticleId,
            symbol = symbol,
            title = title,
            description = "Description $providerArticleId",
            source = "Example Wire",
            url = "https://example.com/$providerArticleId",
            author = "Reporter",
            publishedAt = publishedAt,
        )

    @Test
    fun `fetching news persists and returns articles with real ids`() {
        fakeNewsProvider.articlesToReturn = listOf(
            article("NEWSA", "a1", Instant.parse("2026-01-02T00:00:00Z")),
            article("NEWSA", "a2", Instant.parse("2026-01-03T00:00:00Z")),
        )

        val result = newsService.getNews("newsa", 20)

        assertEquals("NEWSA", result.symbol)
        assertEquals(2, result.articles.size)
        result.articles.forEach { assertNotNull(it.id) }
    }

    @Test
    fun `a repeat call within the freshness window does not call the provider again`() {
        fakeNewsProvider.articlesToReturn = listOf(
            article("NEWSB", "b1", Instant.parse("2026-01-02T00:00:00Z")),
            article("NEWSB", "b2", Instant.parse("2026-01-03T00:00:00Z")),
        )

        newsService.getNews("newsb", 2)
        assertEquals(1, fakeNewsProvider.callCount)

        newsService.getNews("newsb", 2)
        assertEquals(1, fakeNewsProvider.callCount)
    }

    @Test
    fun `a sparse symbol with fewer articles than the limit keeps re-checking the provider`() {
        fakeNewsProvider.articlesToReturn = listOf(
            article("NEWSC", "c1", Instant.parse("2026-01-02T00:00:00Z")),
        )

        newsService.getNews("newsc", 5)
        assertEquals(1, fakeNewsProvider.callCount)

        newsService.getNews("newsc", 5)
        assertEquals(2, fakeNewsProvider.callCount)
    }

    @Test
    fun `duplicate provider article ids are not persisted twice`() {
        fakeNewsProvider.articlesToReturn = listOf(
            article("NEWSD", "d1", Instant.parse("2026-01-02T00:00:00Z")),
        )
        newsService.getNews("newsd", 2)

        // Same provider article id returned again on the next (forced-stale) call.
        fakeNewsProvider.articlesToReturn = listOf(
            article("NEWSD", "d1", Instant.parse("2026-01-02T00:00:00Z")),
        )
        val result = newsService.getNews("newsd", 2)

        assertEquals(1, result.articles.size)
    }

    @Test
    fun `invalid symbol throws InvalidSymbolException`() {
        assertThrows(InvalidSymbolException::class.java) {
            newsService.getNews("this-is-not-a-ticker-123", 20)
        }
    }

    @Test
    fun `invalid limit throws InvalidNewsLimitException`() {
        assertThrows(InvalidNewsLimitException::class.java) {
            newsService.getNews("NEWSE", 0)
        }
        assertThrows(InvalidNewsLimitException::class.java) {
            newsService.getNews("NEWSE", 51)
        }
    }

    @Test
    fun `empty provider results are handled without error`() {
        fakeNewsProvider.articlesToReturn = emptyList()

        val result = newsService.getNews("NEWSF", 20)

        assertTrue(result.articles.isEmpty())
    }

    @Test
    fun `articles are ordered by publishedAt descending`() {
        fakeNewsProvider.articlesToReturn = listOf(
            article("NEWSG", "e1", Instant.parse("2026-01-01T00:00:00Z")),
            article("NEWSG", "e3", Instant.parse("2026-01-05T00:00:00Z")),
            article("NEWSG", "e2", Instant.parse("2026-01-03T00:00:00Z")),
        )

        val result = newsService.getNews("NEWSG", 20)

        assertEquals(listOf("e3", "e2", "e1"), result.articles.map { it.providerArticleId })
    }

    @Test
    fun `concurrent first-time fetches of a brand-new symbol do not throw or duplicate rows`() {
        fakeNewsProvider.articlesToReturn = (1..10).map {
            article("NEWSRACE", "race-$it", Instant.parse("2026-01-01T00:00:00Z").plusSeconds(it * 3600L))
        }

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
                    newsService.getNews("NEWSRACE", 20)
                } catch (ex: Throwable) {
                    errors.add(ex)
                }
            }
        }
        readyLatch.await()
        startLatch.countDown()
        futures.forEach { it.get(30, TimeUnit.SECONDS) }
        executor.shutdown()

        assertTrue(errors.isEmpty()) { "Concurrent getNews calls threw: $errors" }
        val result = newsService.getNews("NEWSRACE", 20)
        assertEquals(10, result.articles.size)
    }
}
