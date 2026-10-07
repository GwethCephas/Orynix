package com.cephCoding.orynix.sentiment.model

import com.cephCoding.orynix.sentiment.domain.AnalyzedSentiment
import com.cephCoding.orynix.sentiment.domain.Sentiment
import com.cephCoding.orynix.sentiment.domain.SentimentResult
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "news_sentiments")
class NewsSentimentEntity(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "news_article_id", nullable = false)
    val newsArticleId: UUID,

    @Column(nullable = false)
    val sentiment: String,

    @Column(nullable = false)
    val score: BigDecimal,

    @Column(nullable = false)
    val confidence: BigDecimal,

    @Column(nullable = false)
    val analyzer: String,

    @Column(nullable = false)
    val model: String,

    @Column(name = "analyzed_at", nullable = false)
    val analyzedAt: Instant = Instant.now(),
) {
    fun toDomain() = SentimentResult(
        id = id,
        newsArticleId = newsArticleId,
        sentiment = Sentiment.valueOf(sentiment),
        score = score,
        confidence = confidence,
        analyzer = analyzer,
        model = model,
        analyzedAt = analyzedAt,
    )

    companion object {
        fun from(newsArticleId: UUID, analyzed: AnalyzedSentiment, analyzer: String, model: String) = NewsSentimentEntity(
            newsArticleId = newsArticleId,
            sentiment = analyzed.sentiment.name,
            score = analyzed.score,
            confidence = analyzed.confidence,
            analyzer = analyzer,
            model = model,
        )
    }
}
