package com.cephCoding.orynix.news.service

import com.cephCoding.orynix.common.exception.InvalidNewsLimitException
import com.cephCoding.orynix.common.exception.InvalidSymbolException
import com.cephCoding.orynix.news.domain.NewsArticle
import com.cephCoding.orynix.news.domain.NewsResult
import com.cephCoding.orynix.news.model.NewsArticleEntity
import com.cephCoding.orynix.news.provider.NewsDataProvider
import com.cephCoding.orynix.news.repository.NewsArticleRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant

@Service
class NewsService(
    private val newsDataProvider: NewsDataProvider,
    private val repository: NewsArticleRepository,
) {
    fun getNews(symbolRaw: String, limitRaw: Int?): NewsResult {
        val symbol = validateSymbol(symbolRaw)
        val limit = validateLimit(limitRaw)

        if (!isFresh(symbol, limit)) {
            val fetched = newsDataProvider.fetchNews(symbol, limit)
            persistNewArticles(symbol, fetched)
        }

        val articles = repository
            .findBySymbolOrderByPublishedAtDesc(symbol, PageRequest.of(0, limit))
            .map(NewsArticleEntity::toDomain)

        return NewsResult(symbol = symbol, articles = articles)
    }

    private fun isFresh(symbol: String, limit: Int): Boolean {
        val persisted = repository.findBySymbolOrderByPublishedAtDesc(symbol, PageRequest.of(0, limit))
        if (persisted.size < limit) {
            return false
        }
        val newestFetch = persisted.maxOf { it.createdAt ?: Instant.MIN }
        return newestFetch.isAfter(Instant.now().minus(FRESHNESS_WINDOW))
    }

    /**
     * Runs in its own transaction so a duplicate-key failure here (two requests racing to persist
     * the same brand-new symbol for the first time) can be caught and swallowed without aborting
     * the caller's transaction — same race condition class fixed in MarketDataService.
     * persistNewCandles; applied here proactively rather than waiting to rediscover it.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun persistNewArticles(symbol: String, fetched: List<NewsArticle>) {
        val existingProviderArticleIds = repository
            .findBySymbolAndProviderArticleIdIn(symbol, fetched.map { it.providerArticleId })
            .map { it.providerArticleId }
            .toSet()
        val newArticles = fetched.filterNot { it.providerArticleId in existingProviderArticleIds }
        try {
            repository.saveAllAndFlush(newArticles.map(NewsArticleEntity::from))
        } catch (ex: DataIntegrityViolationException) {
            // Flushed eagerly above specifically so this constraint violation surfaces here,
            // inside the try, rather than at commit time after this method has already returned.
        }
    }

    private fun validateSymbol(symbolRaw: String): String {
        val symbol = symbolRaw.trim().uppercase()
        if (!SYMBOL_PATTERN.matches(symbol)) {
            throw InvalidSymbolException(symbolRaw)
        }
        return symbol
    }

    private fun validateLimit(limitRaw: Int?): Int {
        val limit = limitRaw ?: DEFAULT_LIMIT
        if (limit !in 1..MAX_LIMIT) {
            throw InvalidNewsLimitException(limit)
        }
        return limit
    }

    companion object {
        private val SYMBOL_PATTERN = Regex("^[A-Z.]{1,10}$")
        private const val DEFAULT_LIMIT = 20
        private const val MAX_LIMIT = 50
        private val FRESHNESS_WINDOW: Duration = Duration.ofMinutes(15)
    }
}
