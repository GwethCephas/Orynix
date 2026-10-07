package com.cephCoding.orynix.sentiment.dto

import com.cephCoding.orynix.sentiment.domain.Sentiment
import com.cephCoding.orynix.sentiment.domain.SentimentAggregate
import java.math.BigDecimal

data class SentimentAggregateResponse(
    val symbol: String,
    val articleCount: Int,
    val positiveCount: Int,
    val neutralCount: Int,
    val negativeCount: Int,
    val averageScore: BigDecimal,
    val overallSentiment: Sentiment,
) {
    companion object {
        fun from(aggregate: SentimentAggregate) = SentimentAggregateResponse(
            symbol = aggregate.symbol,
            articleCount = aggregate.articleCount,
            positiveCount = aggregate.positiveCount,
            neutralCount = aggregate.neutralCount,
            negativeCount = aggregate.negativeCount,
            averageScore = aggregate.averageScore,
            overallSentiment = aggregate.overallSentiment,
        )
    }
}
