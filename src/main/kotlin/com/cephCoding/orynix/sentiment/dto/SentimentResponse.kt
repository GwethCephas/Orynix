package com.cephCoding.orynix.sentiment.dto

import com.cephCoding.orynix.sentiment.domain.Sentiment
import com.cephCoding.orynix.sentiment.domain.SentimentResult
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class SentimentResponse(
    val newsArticleId: UUID,
    val sentiment: Sentiment,
    val score: BigDecimal,
    val confidence: BigDecimal,
    val analyzer: String,
    val model: String,
    val analyzedAt: Instant?,
) {
    companion object {
        fun from(result: SentimentResult) = SentimentResponse(
            newsArticleId = result.newsArticleId,
            sentiment = result.sentiment,
            score = result.score,
            confidence = result.confidence,
            analyzer = result.analyzer,
            model = result.model,
            analyzedAt = result.analyzedAt,
        )
    }
}
