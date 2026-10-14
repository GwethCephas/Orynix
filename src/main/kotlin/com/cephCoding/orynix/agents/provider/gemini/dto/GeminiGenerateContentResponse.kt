package com.cephCoding.orynix.agents.provider.gemini.dto

data class GeminiGenerateContentResponse(
    val candidates: List<GeminiCandidate>? = null,
)

data class GeminiCandidate(
    val content: GeminiContent? = null,
    val finishReason: String? = null,
)

/**
 * The model's actual structured JSON output - the response schema constrains its shape, but it is
 * still only ever returned as a JSON-encoded STRING inside a text part, never a native object on
 * this envelope. Mirrors the five fields ModelAnalysis needs: marketAssessment, scenarios (each
 * label/narrative/confidence), tradeSetups (each direction/entryPrice/stopLossPrice/
 * takeProfitPrice/rationale). Parsed by GeminiMarketAnalysisProvider's companion-object parser,
 * never trusted without the structural + semantic validation that follows.
 */
data class GeminiModelAnalysisPayload(
    val marketAssessment: String? = null,
    val scenarios: List<GeminiModelScenario>? = null,
    val tradeSetups: List<GeminiModelTradeSetup>? = null,
)

data class GeminiModelScenario(
    val label: String? = null,
    val narrative: String? = null,
    val confidence: String? = null,
)

data class GeminiModelTradeSetup(
    val direction: String? = null,
    val entryPrice: java.math.BigDecimal? = null,
    val stopLossPrice: java.math.BigDecimal? = null,
    val takeProfitPrice: java.math.BigDecimal? = null,
    val rationale: String? = null,
)
