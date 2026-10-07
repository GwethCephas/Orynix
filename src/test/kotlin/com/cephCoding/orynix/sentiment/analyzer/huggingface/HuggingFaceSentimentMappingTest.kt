package com.cephCoding.orynix.sentiment.analyzer.huggingface

import com.cephCoding.orynix.sentiment.analyzer.huggingface.dto.HuggingFaceSentimentScore
import com.cephCoding.orynix.sentiment.domain.Sentiment
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class HuggingFaceSentimentMappingTest {

    @Test
    fun `clear positive scores map to POSITIVE with the correct score and confidence`() {
        val scores = listOf(
            HuggingFaceSentimentScore("positive", 0.91),
            HuggingFaceSentimentScore("negative", 0.03),
            HuggingFaceSentimentScore("neutral", 0.06),
        )

        val result = HuggingFaceSentimentAnalyzer.toAnalyzedSentiment(scores)

        assertEquals(Sentiment.POSITIVE, result.sentiment)
        assertEquals(BigDecimal("0.8800"), result.score)
        assertEquals(BigDecimal("0.9100"), result.confidence)
    }

    @Test
    fun `clear negative scores map to NEGATIVE`() {
        val scores = listOf(
            HuggingFaceSentimentScore("positive", 0.05),
            HuggingFaceSentimentScore("negative", 0.88),
            HuggingFaceSentimentScore("neutral", 0.07),
        )

        val result = HuggingFaceSentimentAnalyzer.toAnalyzedSentiment(scores)

        assertEquals(Sentiment.NEGATIVE, result.sentiment)
        assertEquals(BigDecimal("-0.8300"), result.score)
        assertEquals(BigDecimal("0.8800"), result.confidence)
    }

    @Test
    fun `dominant neutral score maps to NEUTRAL with a near-zero compound score`() {
        val scores = listOf(
            HuggingFaceSentimentScore("positive", 0.20),
            HuggingFaceSentimentScore("negative", 0.18),
            HuggingFaceSentimentScore("neutral", 0.62),
        )

        val result = HuggingFaceSentimentAnalyzer.toAnalyzedSentiment(scores)

        assertEquals(Sentiment.NEUTRAL, result.sentiment)
        assertEquals(BigDecimal("0.0200"), result.score)
        assertEquals(BigDecimal("0.6200"), result.confidence)
    }

    @Test
    fun `label matching is case-insensitive`() {
        val scores = listOf(
            HuggingFaceSentimentScore("POSITIVE", 0.75),
            HuggingFaceSentimentScore("NEGATIVE", 0.10),
            HuggingFaceSentimentScore("NEUTRAL", 0.15),
        )

        val result = HuggingFaceSentimentAnalyzer.toAnalyzedSentiment(scores)

        assertEquals(Sentiment.POSITIVE, result.sentiment)
        assertEquals(BigDecimal("0.6500"), result.score)
    }

    @Test
    fun `rounds to scale 4 using HALF_UP`() {
        val scores = listOf(
            HuggingFaceSentimentScore("positive", 1.0 / 3.0),
            HuggingFaceSentimentScore("negative", 1.0 / 3.0),
            HuggingFaceSentimentScore("neutral", 1.0 / 3.0),
        )

        val result = HuggingFaceSentimentAnalyzer.toAnalyzedSentiment(scores)

        assertEquals(4, result.score.scale())
        assertEquals(4, result.confidence.scale())
    }
}
