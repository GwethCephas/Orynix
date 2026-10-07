package com.cephCoding.orynix.sentiment.analyzer.huggingface

import com.cephCoding.orynix.common.exception.SentimentAnalyzerException
import com.cephCoding.orynix.sentiment.analyzer.SentimentAnalyzer
import com.cephCoding.orynix.sentiment.analyzer.huggingface.dto.HuggingFaceSentimentRequest
import com.cephCoding.orynix.sentiment.analyzer.huggingface.dto.HuggingFaceSentimentScore
import com.cephCoding.orynix.sentiment.domain.AnalyzedSentiment
import com.cephCoding.orynix.sentiment.domain.Sentiment
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import java.math.BigDecimal
import java.math.RoundingMode

@Component
class HuggingFaceSentimentAnalyzer(
    restClientBuilder: RestClient.Builder,
    private val properties: HuggingFaceProperties,
) : SentimentAnalyzer {

    override val analyzerName = "huggingface"
    override val modelName get() = properties.model

    private val restClient = restClientBuilder.baseUrl(properties.baseUrl).build()

    override fun analyze(text: String): AnalyzedSentiment {
        val response = try {
            restClient.post()
                .uri("/{model}", properties.model)
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${properties.apiKey}")
                .body(HuggingFaceSentimentRequest(inputs = text))
                .retrieve()
                .body(Array<HuggingFaceSentimentScore>::class.java)
        } catch (ex: RestClientException) {
            throw SentimentAnalyzerException("Sentiment analyzer request failed")
        } ?: throw SentimentAnalyzerException("Sentiment analyzer request failed")

        if (response.isEmpty()) {
            throw SentimentAnalyzerException("Sentiment analyzer request failed")
        }

        return toAnalyzedSentiment(response.toList())
    }

    companion object {
        private const val SCORE_SCALE = 4

        fun toAnalyzedSentiment(scores: List<HuggingFaceSentimentScore>): AnalyzedSentiment {
            val byLabel = scores.associateBy { it.label.lowercase() }
            val positive = byLabel["positive"]?.score ?: 0.0
            val negative = byLabel["negative"]?.score ?: 0.0
            val winner = scores.maxBy { it.score }
            val sentiment = when (winner.label.lowercase()) {
                "positive" -> Sentiment.POSITIVE
                "negative" -> Sentiment.NEGATIVE
                else -> Sentiment.NEUTRAL
            }
            return AnalyzedSentiment(
                sentiment = sentiment,
                score = BigDecimal.valueOf(positive - negative).setScale(SCORE_SCALE, RoundingMode.HALF_UP),
                confidence = BigDecimal.valueOf(winner.score).setScale(SCORE_SCALE, RoundingMode.HALF_UP),
            )
        }
    }
}
