package com.cephCoding.orynix.support

import com.cephCoding.orynix.sentiment.analyzer.SentimentAnalyzer
import com.cephCoding.orynix.sentiment.domain.AnalyzedSentiment
import com.cephCoding.orynix.sentiment.domain.Sentiment
import java.math.BigDecimal

class FakeSentimentAnalyzer : SentimentAnalyzer {
    override val analyzerName: String = "fake-analyzer"
    override val modelName: String = "fake-model"

    var sentimentToReturn: AnalyzedSentiment = AnalyzedSentiment(
        sentiment = Sentiment.NEUTRAL,
        score = BigDecimal.ZERO,
        confidence = BigDecimal.ZERO,
    )
    var failure: RuntimeException? = null
    var callCount: Int = 0
        private set

    override fun analyze(text: String): AnalyzedSentiment {
        callCount++
        failure?.let { throw it }
        return sentimentToReturn
    }

    fun reset() {
        sentimentToReturn = AnalyzedSentiment(
            sentiment = Sentiment.NEUTRAL,
            score = BigDecimal.ZERO,
            confidence = BigDecimal.ZERO,
        )
        failure = null
        callCount = 0
    }
}
