package com.cephCoding.orynix.sentiment.service

import com.cephCoding.orynix.news.domain.NewsArticle
import com.cephCoding.orynix.news.service.NewsService
import com.cephCoding.orynix.sentiment.analyzer.SentimentAnalyzer
import com.cephCoding.orynix.sentiment.domain.AnalyzedSentiment
import com.cephCoding.orynix.sentiment.domain.Sentiment
import com.cephCoding.orynix.sentiment.domain.SentimentAggregate
import com.cephCoding.orynix.sentiment.domain.SentimentResult
import com.cephCoding.orynix.sentiment.model.NewsSentimentEntity
import com.cephCoding.orynix.sentiment.repository.NewsSentimentRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

@Service
class SentimentService(
    private val newsService: NewsService,
    private val analyzer: SentimentAnalyzer,
    private val repository: NewsSentimentRepository,
) {
    fun getOrAnalyze(newsId: UUID): SentimentResult {
        val article = newsService.getArticleById(newsId)
        return repository.findByNewsArticleIdAndAnalyzerAndModel(article.id!!, analyzer.analyzerName, analyzer.modelName)
            ?.toDomain()
            ?: analyzeAndPersist(article)
    }

    fun getAggregate(symbolRaw: String, limitRaw: Int?): SentimentAggregate {
        val news = newsService.getNews(symbolRaw, limitRaw)
        val candidates = news.articles.take(MAX_ARTICLES_ANALYZED_PER_REQUEST)

        val existingByArticleId = repository
            .findByNewsArticleIdInAndAnalyzerAndModel(candidates.mapNotNull { it.id }, analyzer.analyzerName, analyzer.modelName)
            .associateBy { it.newsArticleId }

        val results = candidates.map { article ->
            existingByArticleId[article.id]?.toDomain() ?: analyzeAndPersist(article)
        }

        return aggregate(news.symbol, results)
    }

    private fun analyzeAndPersist(article: NewsArticle): SentimentResult {
        val analyzed = analyzer.analyze(buildText(article))
        return persist(article.id!!, analyzed)
    }

    private fun buildText(article: NewsArticle): String =
        // title alone is always sufficient — the news feature's own provider mapper already skips
        // any article missing a title, so there is no "no usable text" case to guard against here.
        article.description?.let { "${article.title}. $it" } ?: article.title

    /**
     * Runs in its own transaction so a duplicate-key failure here (two requests racing to analyze
     * the same article for the first time) can be caught and swallowed — same race condition class
     * fixed in MarketDataService/NewsService. Unlike those, a single-article lookup has no natural
     * re-query afterward, so the loser explicitly re-reads the winner's row rather than returning a
     * result whose id/analyzedAt were never actually persisted.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun persist(articleId: UUID, analyzed: AnalyzedSentiment): SentimentResult {
        val entity = NewsSentimentEntity.from(articleId, analyzed, analyzer.analyzerName, analyzer.modelName)
        return try {
            repository.saveAndFlush(entity).toDomain()
        } catch (ex: DataIntegrityViolationException) {
            repository.findByNewsArticleIdAndAnalyzerAndModel(articleId, analyzer.analyzerName, analyzer.modelName)!!.toDomain()
        }
    }

    private fun aggregate(symbol: String, results: List<SentimentResult>): SentimentAggregate {
        val positiveCount = results.count { it.sentiment == Sentiment.POSITIVE }
        val negativeCount = results.count { it.sentiment == Sentiment.NEGATIVE }
        val neutralCount = results.size - positiveCount - negativeCount

        val averageScore = if (results.isEmpty()) {
            BigDecimal.ZERO.setScale(SCORE_SCALE, RoundingMode.HALF_UP)
        } else {
            results.sumOf { it.score }.divide(BigDecimal(results.size), SCORE_SCALE, RoundingMode.HALF_UP)
        }

        val overallSentiment = when {
            averageScore.compareTo(POSITIVE_THRESHOLD) >= 0 -> Sentiment.POSITIVE
            averageScore.compareTo(NEGATIVE_THRESHOLD) <= 0 -> Sentiment.NEGATIVE
            else -> Sentiment.NEUTRAL
        }

        return SentimentAggregate(
            symbol = symbol,
            articleCount = results.size,
            positiveCount = positiveCount,
            neutralCount = neutralCount,
            negativeCount = negativeCount,
            averageScore = averageScore,
            overallSentiment = overallSentiment,
        )
    }

    companion object {
        private const val MAX_ARTICLES_ANALYZED_PER_REQUEST = 10
        private const val SCORE_SCALE = 4
        private val POSITIVE_THRESHOLD = BigDecimal("0.15")
        private val NEGATIVE_THRESHOLD = BigDecimal("-0.15")
    }
}
