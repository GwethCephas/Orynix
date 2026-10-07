package com.cephCoding.orynix.sentiment.analyzer

import com.cephCoding.orynix.sentiment.domain.AnalyzedSentiment

interface SentimentAnalyzer {
    val analyzerName: String
    val modelName: String
    fun analyze(text: String): AnalyzedSentiment
}
