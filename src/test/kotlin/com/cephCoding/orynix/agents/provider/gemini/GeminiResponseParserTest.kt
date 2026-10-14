package com.cephCoding.orynix.agents.provider.gemini

import com.cephCoding.orynix.agents.domain.AnalysisSource
import com.cephCoding.orynix.agents.domain.Confidence
import com.cephCoding.orynix.agents.domain.ScenarioLabel
import com.cephCoding.orynix.agents.domain.TradeDirection
import com.cephCoding.orynix.common.exception.MarketAnalysisResponseException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.math.BigDecimal

class GeminiResponseParserTest {

    private val objectMapper = jacksonObjectMapper()

    @Test
    fun `a well-formed response parses into a ModelAnalysis tagged as a real model response`() {
        val json = """
            {
              "marketAssessment": "Price is consolidating above the 50-day average.",
              "scenarios": [
                {"label": "BULLISH", "narrative": "Breakout above resistance.", "confidence": "MEDIUM"}
              ],
              "tradeSetups": [
                {"direction": "LONG", "entryPrice": 100.50, "stopLossPrice": 95.00, "takeProfitPrice": 115.00, "rationale": "Trend continuation."}
              ]
            }
        """.trimIndent()

        val result = GeminiMarketAnalysisProvider.parseModelAnalysis(json, objectMapper, "gemini", "gemini-2.0-flash")

        assertEquals(AnalysisSource.MODEL, result.provenance.source)
        assertEquals("gemini", result.provenance.providerName)
        assertEquals("gemini-2.0-flash", result.provenance.model)
        assertEquals("Price is consolidating above the 50-day average.", result.marketAssessment)
        assertEquals(1, result.scenarios.size)
        assertEquals(ScenarioLabel.BULLISH, result.scenarios[0].label)
        assertEquals(Confidence.MEDIUM, result.scenarios[0].confidence)
        assertEquals(1, result.tradeSetups.size)
        assertEquals(TradeDirection.LONG, result.tradeSetups[0].direction)
        assertEquals(BigDecimal("100.50"), result.tradeSetups[0].entryPrice)
    }

    @Test
    fun `empty scenarios and tradeSetups lists are valid, never forced`() {
        val json = """{"marketAssessment": "No clear setup today.", "scenarios": [], "tradeSetups": []}"""

        val result = GeminiMarketAnalysisProvider.parseModelAnalysis(json, objectMapper, "gemini", "gemini-2.0-flash")

        assertEquals(0, result.scenarios.size)
        assertEquals(0, result.tradeSetups.size)
    }

    @Test
    fun `a scenario with no confidence is valid`() {
        val json = """
            {"marketAssessment": "x", "scenarios": [{"label": "NEUTRAL", "narrative": "y"}], "tradeSetups": []}
        """.trimIndent()

        val result = GeminiMarketAnalysisProvider.parseModelAnalysis(json, objectMapper, "gemini", "gemini-2.0-flash")

        assertEquals(null, result.scenarios[0].confidence)
    }

    @Test
    fun `malformed JSON is rejected with MarketAnalysisResponseException`() {
        assertThrows(MarketAnalysisResponseException::class.java) {
            GeminiMarketAnalysisProvider.parseModelAnalysis("not json", objectMapper, "gemini", "gemini-2.0-flash")
        }
    }

    @Test
    fun `a missing marketAssessment is rejected`() {
        val json = """{"scenarios": [], "tradeSetups": []}"""
        assertThrows(MarketAnalysisResponseException::class.java) {
            GeminiMarketAnalysisProvider.parseModelAnalysis(json, objectMapper, "gemini", "gemini-2.0-flash")
        }
    }

    @Test
    fun `a blank marketAssessment is rejected`() {
        val json = """{"marketAssessment": "   ", "scenarios": [], "tradeSetups": []}"""
        assertThrows(MarketAnalysisResponseException::class.java) {
            GeminiMarketAnalysisProvider.parseModelAnalysis(json, objectMapper, "gemini", "gemini-2.0-flash")
        }
    }

    @Test
    fun `an invalid scenario label enum value is rejected`() {
        val json = """
            {"marketAssessment": "x", "scenarios": [{"label": "SIDEWAYS", "narrative": "y"}], "tradeSetups": []}
        """.trimIndent()
        assertThrows(MarketAnalysisResponseException::class.java) {
            GeminiMarketAnalysisProvider.parseModelAnalysis(json, objectMapper, "gemini", "gemini-2.0-flash")
        }
    }

    @Test
    fun `a non-positive trade setup price is rejected`() {
        val json = """
            {"marketAssessment": "x", "scenarios": [],
             "tradeSetups": [{"direction": "LONG", "entryPrice": -5, "stopLossPrice": 1, "rationale": "y"}]}
        """.trimIndent()
        assertThrows(MarketAnalysisResponseException::class.java) {
            GeminiMarketAnalysisProvider.parseModelAnalysis(json, objectMapper, "gemini", "gemini-2.0-flash")
        }
    }

    @Test
    fun `a trade setup missing rationale is rejected`() {
        val json = """
            {"marketAssessment": "x", "scenarios": [],
             "tradeSetups": [{"direction": "LONG", "entryPrice": 10, "stopLossPrice": 5}]}
        """.trimIndent()
        assertThrows(MarketAnalysisResponseException::class.java) {
            GeminiMarketAnalysisProvider.parseModelAnalysis(json, objectMapper, "gemini", "gemini-2.0-flash")
        }
    }
}
