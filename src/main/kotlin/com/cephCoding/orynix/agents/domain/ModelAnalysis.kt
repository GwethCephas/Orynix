package com.cephCoding.orynix.agents.domain

import java.math.BigDecimal

/**
 * The provider's structured output, pre-response-DTO. Produced by MarketAnalysisProvider.analyze
 * and validated/reshaped by MarketAnalysisService before becoming a MarketAnalysisResponse.
 */
data class ModelAnalysis(
    val provenance: AnalysisProvenance,
    val marketAssessment: String,
    val scenarios: List<ModelScenario>,
    val tradeSetups: List<ModelTradeSetup>,
)

enum class ScenarioLabel { BULLISH, BEARISH, NEUTRAL }

enum class Confidence { LOW, MEDIUM, HIGH }

data class ModelScenario(
    val label: ScenarioLabel,
    val narrative: String,
    val confidence: Confidence?,
)

enum class TradeDirection { LONG, SHORT }

/**
 * Only price LEVELS and rationale - no monetary values anywhere on this type. Quantity/estimated
 * loss/estimated gain/risk-reward are computed exclusively by RiskCalculatorService once a setup
 * survives validation (CLAUDE.md Section 20) - the model never performs that arithmetic itself.
 */
data class ModelTradeSetup(
    val direction: TradeDirection,
    val entryPrice: BigDecimal,
    val stopLossPrice: BigDecimal,
    val takeProfitPrice: BigDecimal?,
    val rationale: String,
)
