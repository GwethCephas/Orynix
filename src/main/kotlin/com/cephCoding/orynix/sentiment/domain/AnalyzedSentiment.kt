package com.cephCoding.orynix.sentiment.domain

import java.math.BigDecimal

data class AnalyzedSentiment(
    val sentiment: Sentiment,
    val score: BigDecimal,
    val confidence: BigDecimal,
)
