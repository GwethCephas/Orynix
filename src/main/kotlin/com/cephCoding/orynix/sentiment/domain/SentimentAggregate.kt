package com.cephCoding.orynix.sentiment.domain

import java.math.BigDecimal

data class SentimentAggregate(
    val symbol: String,
    val articleCount: Int,
    val positiveCount: Int,
    val neutralCount: Int,
    val negativeCount: Int,
    val averageScore: BigDecimal,
    val overallSentiment: Sentiment,
)
