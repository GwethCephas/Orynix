package com.cephCoding.orynix.sentiment.domain

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class SentimentResult(
    val id: UUID?,
    val newsArticleId: UUID,
    val sentiment: Sentiment,
    val score: BigDecimal,
    val confidence: BigDecimal,
    val analyzer: String,
    val model: String,
    val analyzedAt: Instant?,
)
