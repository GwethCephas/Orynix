package com.cephCoding.orynix.agents.dto

import com.cephCoding.orynix.agents.domain.AnalysisProvenance
import com.cephCoding.orynix.agents.domain.AnalysisSource
import com.cephCoding.orynix.agents.domain.Confidence
import com.cephCoding.orynix.agents.domain.ModelAnalysis
import com.cephCoding.orynix.agents.domain.ScenarioLabel
import com.cephCoding.orynix.agents.domain.TradeDirection
import com.cephCoding.orynix.agents.domain.TradeSetupProposal
import com.cephCoding.orynix.instrument.domain.AssetType
import com.cephCoding.orynix.marketintelligence.domain.ContextSectionStatus
import com.cephCoding.orynix.marketintelligence.domain.MarketIntelligenceContext
import com.cephCoding.orynix.riskcalculator.domain.RiskCalculationResult
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class AnalysisProvenanceResponse(
    val source: AnalysisSource,
    val providerName: String,
    val model: String,
) {
    companion object {
        fun from(provenance: AnalysisProvenance) =
            AnalysisProvenanceResponse(source = provenance.source, providerName = provenance.providerName, model = provenance.model)
    }
}

data class ScenarioResponse(
    val label: ScenarioLabel,
    val narrative: String,
    val confidence: Confidence?,
)

/**
 * Thin passthrough of RiskCalculationResult - the numbers are never recomputed here, only
 * reshaped into the response, since RiskCalculatorService is the sole authority on this
 * arithmetic (CLAUDE.md Section 20).
 */
data class RiskSizingResponse(
    val accountCurrency: String,
    val equity: BigDecimal,
    val quantity: BigDecimal?,
    val estimatedLossAtStop: BigDecimal?,
    val estimatedGainAtTakeProfit: BigDecimal?,
    val riskRewardRatio: BigDecimal?,
) {
    companion object {
        fun from(result: RiskCalculationResult) = RiskSizingResponse(
            accountCurrency = result.accountCurrency,
            equity = result.equity,
            quantity = result.quantity,
            estimatedLossAtStop = result.estimatedLossAtStop,
            estimatedGainAtTakeProfit = result.estimatedGainAtTakeProfit,
            riskRewardRatio = result.riskRewardRatio,
        )
    }
}

data class TradeSetupProposalResponse(
    val id: UUID,
    val direction: TradeDirection,
    val entryPrice: BigDecimal,
    val stopLossPrice: BigDecimal,
    val takeProfitPrice: BigDecimal?,
    val rationale: String,
    val sizing: RiskSizingResponse?,
    val limitations: List<String>,
) {
    companion object {
        fun from(proposal: TradeSetupProposal) = TradeSetupProposalResponse(
            id = proposal.id,
            direction = proposal.setup.direction,
            entryPrice = proposal.setup.entryPrice,
            stopLossPrice = proposal.setup.stopLossPrice,
            takeProfitPrice = proposal.setup.takeProfitPrice,
            rationale = proposal.setup.rationale,
            sizing = proposal.sizing?.let { RiskSizingResponse.from(it) },
            limitations = proposal.limitations,
        )
    }
}

data class SectionStatusResponse(
    val status: ContextSectionStatus,
    val message: String?,
)

/**
 * Mirrors ContextSectionResponse's envelope shape on purpose (CLAUDE.md Section 17) so the
 * frontend can render it with the exact same per-section status component, zero new rendering
 * logic needed. No nullable fields - every section is always reported, even NO_DATA.
 */
data class EvidenceStatusResponse(
    val marketData: SectionStatusResponse,
    val technicalAnalysis: SectionStatusResponse,
    val signals: SectionStatusResponse,
    val companyInformation: SectionStatusResponse,
    val instrumentInformation: SectionStatusResponse,
    val news: SectionStatusResponse,
    val sentiment: SectionStatusResponse,
) {
    companion object {
        fun from(context: MarketIntelligenceContext) = EvidenceStatusResponse(
            marketData = SectionStatusResponse(context.marketData.status, context.marketData.message),
            technicalAnalysis = SectionStatusResponse(context.technicalAnalysis.status, context.technicalAnalysis.message),
            signals = SectionStatusResponse(context.signals.status, context.signals.message),
            companyInformation = SectionStatusResponse(context.companyInformation.status, context.companyInformation.message),
            instrumentInformation = SectionStatusResponse(context.instrumentInformation.status, context.instrumentInformation.message),
            news = SectionStatusResponse(context.news.status, context.news.message),
            sentiment = SectionStatusResponse(context.sentiment.status, context.sentiment.message),
        )
    }
}

data class MarketAnalysisResponse(
    val symbol: String,
    val assetType: AssetType,
    val asOf: Instant,
    val provenance: AnalysisProvenanceResponse,
    val marketAssessment: String,
    val scenarios: List<ScenarioResponse>,
    val tradeSetups: List<TradeSetupProposalResponse>,
    val evidenceStatus: EvidenceStatusResponse,
) {
    companion object {
        fun from(context: MarketIntelligenceContext, modelAnalysis: ModelAnalysis, tradeSetups: List<TradeSetupProposal>) =
            MarketAnalysisResponse(
                symbol = context.symbol,
                assetType = context.assetType,
                asOf = context.asOf,
                provenance = AnalysisProvenanceResponse.from(modelAnalysis.provenance),
                marketAssessment = modelAnalysis.marketAssessment,
                scenarios = modelAnalysis.scenarios.map { ScenarioResponse(it.label, it.narrative, it.confidence) },
                tradeSetups = tradeSetups.map { TradeSetupProposalResponse.from(it) },
                evidenceStatus = EvidenceStatusResponse.from(context),
            )
    }
}
