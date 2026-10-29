package com.cephCoding.orynix.agents.service

import com.cephCoding.orynix.agents.domain.AnalysisProvenance
import com.cephCoding.orynix.agents.domain.ModelTradeSetup
import com.cephCoding.orynix.agents.domain.TradeDirection
import com.cephCoding.orynix.agents.domain.TradeSetupProposal
import com.cephCoding.orynix.agents.dto.MarketAnalysisRequest
import com.cephCoding.orynix.agents.dto.MarketAnalysisResponse
import com.cephCoding.orynix.agents.model.AiTradeSetupProposalEntity
import com.cephCoding.orynix.agents.provider.MarketAnalysisProvider
import com.cephCoding.orynix.agents.repository.AiTradeSetupProposalRepository
import com.cephCoding.orynix.marketintelligence.domain.MarketIntelligenceContext
import com.cephCoding.orynix.marketintelligence.service.MarketIntelligenceService
import com.cephCoding.orynix.riskcalculator.dto.RiskCalculationRequest
import com.cephCoding.orynix.riskcalculator.service.RiskCalculatorService
import com.cephCoding.orynix.tradingaccount.service.TradingAccountService
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.util.UUID

/**
 * Orchestrates the first AI Market Analysis Agent (CLAUDE.md Section 20). Deliberately not
 * @Transactional - nothing here is persisted, and none of the underlying services it calls are
 * transactional either.
 *
 * Two distinct failure classes, handled differently:
 * 1. Model/provider unavailable or unparseable - MarketAnalysisProviderException/
 *    MarketAnalysisResponseException propagate uncaught (-> 502 via GlobalExceptionHandler). There
 *    is no meaningful partial analysis without any model output to build the narrative from.
 * 2. One evidence section unavailable (forex news/sentiment, a thinly-traded symbol's missing
 *    fundamentals) - not an exception. It already arrives as a NO_DATA/ERROR ContextSection from
 *    MarketIntelligenceService (whose own partial-failure handling is untouched here), flows
 *    through EvidenceContextBuilder unchanged, and is surfaced to the caller as normal.
 */
@Service
class MarketAnalysisService(
    private val marketIntelligenceService: MarketIntelligenceService,
    private val evidenceContextBuilder: EvidenceContextBuilder,
    private val provider: MarketAnalysisProvider,
    private val riskCalculatorService: RiskCalculatorService,
    private val tradingAccountService: TradingAccountService,
    private val proposalRepository: AiTradeSetupProposalRepository,
) {

    fun analyze(userId: UUID, request: MarketAnalysisRequest): MarketAnalysisResponse {
        // Validated eagerly, before the (expensive) market-intelligence fetch and model call, and
        // regardless of whether the model ends up proposing a sizeable trade setup - a reference
        // to a profile the caller doesn't own must 404 unconditionally, not only when attachSizing
        // happens to need it. The looked-up profile itself is discarded here; RiskCalculatorService
        // performs its own ownership-checked lookup again inside attachSizing when actually sizing.
        request.accountProfileId?.let { tradingAccountService.findOwnedProfile(userId, it) }

        val miContext = marketIntelligenceService.getContext(request.symbol)
        val evidence = evidenceContextBuilder.build(miContext)
        val modelAnalysis = provider.analyze(evidence)

        val validatedSetups = modelAnalysis.tradeSetups.mapNotNull { validateAgainstMarket(it, miContext) }
        val persisted = persistProposals(userId, miContext, modelAnalysis.provenance, validatedSetups)
        val tradeSetups = persisted.map { (id, setup) -> attachSizing(id, setup, userId, request, miContext.symbol) }

        return MarketAnalysisResponse.from(miContext, modelAnalysis, tradeSetups)
    }

    /**
     * Persists each setup that survived semantic validation as a durable, server-verifiable
     * AiTradeSetupProposalEntity (CLAUDE.md Section 21's Integration A) - the record a later
     * POST /trade-setups/from-ai-proposal call looks up by id, copying symbol/direction/entry/
     * stop/target/rationale/provenance from this row rather than trusting client-resubmitted
     * values. Ids are client-assigned (CLAUDE.md Section 14), so they're already known before
     * saveAll runs - no need to read back the saved/merged copies to learn them. Deliberately not
     * wrapped in @Transactional: a plain saveAll on a Spring Data repository is already
     * transactional on its own, and this method is a pure batch insert with no other write to
     * keep atomic with it.
     */
    private fun persistProposals(
        userId: UUID,
        context: MarketIntelligenceContext,
        provenance: AnalysisProvenance,
        setups: List<ModelTradeSetup>,
    ): List<Pair<UUID, ModelTradeSetup>> {
        if (setups.isEmpty()) return emptyList()
        val pairs = setups.map { setup ->
            AiTradeSetupProposalEntity(
                userId = userId,
                assetType = context.assetType.name,
                symbol = context.symbol,
                direction = setup.direction.name,
                entryPrice = setup.entryPrice,
                stopLossPrice = setup.stopLossPrice,
                takeProfitPrice = setup.takeProfitPrice,
                rationale = setup.rationale,
                providerName = provenance.providerName,
                model = provenance.model,
                analysisAsOf = context.asOf,
            ) to setup
        }
        proposalRepository.saveAll(pairs.map { it.first })
        return pairs.map { (entity, setup) -> entity.id to setup }
    }

    /**
     * sizing is null on every path that can't produce a number, always paired with a
     * human-readable reason in limitations - never a silently-absent field, never a fabricated
     * quantity (CLAUDE.md Section 20). RiskCalculatorService itself remains the sole authority on
     * the arithmetic; this method only decides whether to call it and with what inputs.
     */
    private fun attachSizing(id: UUID, setup: ModelTradeSetup, userId: UUID, request: MarketAnalysisRequest, symbol: String): TradeSetupProposal {
        if (setup.direction == TradeDirection.SHORT) {
            return TradeSetupProposal(id, setup, null, listOf(MSG_SHORT_UNSUPPORTED))
        }
        if (request.accountProfileId == null && request.equity == null) {
            return TradeSetupProposal(id, setup, null, listOf(MSG_NO_ACCOUNT_CONTEXT))
        }
        val result = riskCalculatorService.calculate(
            userId,
            RiskCalculationRequest(
                symbol = symbol,
                accountProfileId = request.accountProfileId,
                equity = request.equity,
                accountCurrency = request.accountCurrency,
                riskPercentPerTrade = request.riskPercentPerTrade,
                entryPrice = setup.entryPrice,
                stopLossPrice = setup.stopLossPrice,
                takeProfitPrice = setup.takeProfitPrice,
            ),
        )
        return TradeSetupProposal(id, setup, result, result.limitations)
    }

    companion object {
        private const val MAX_ATR_MULTIPLE = 10
        private val MAX_PERCENT_DEVIATION = BigDecimal("0.5")
        private const val MSG_SHORT_UNSUPPORTED = "Deterministic position sizing is currently long-only"
        private const val MSG_NO_ACCOUNT_CONTEXT = "No account context provided; sizing requires equity or a trading account profile"

        /**
         * Semantic-sanity validation against evidence already in hand - the second of two
         * validation layers (the first, structural layer runs inside the provider's response
         * parser). A setup failing this check is dropped silently from the result, never thrown
         * as an exception: the caller's request was fine, the model simply floated an implausible
         * or internally inconsistent level for that one setup. A companion function (not an
         * instance method), matching BacktestService.computeWarmupFromDate's precedent, so it's
         * testable without constructing the full service and its dependencies.
         */
        internal fun validateAgainstMarket(setup: ModelTradeSetup, context: MarketIntelligenceContext): ModelTradeSetup? {
            val directionConsistent = when (setup.direction) {
                TradeDirection.LONG -> setup.stopLossPrice < setup.entryPrice &&
                    (setup.takeProfitPrice == null || setup.takeProfitPrice > setup.entryPrice)
                TradeDirection.SHORT -> setup.stopLossPrice > setup.entryPrice &&
                    (setup.takeProfitPrice == null || setup.takeProfitPrice < setup.entryPrice)
            }
            if (!directionConsistent) return null

            // Without a reference close price there is nothing to sanity-check the level against -
            // direction/stop/take-profit consistency alone already passed, so the setup still stands.
            val latestClose = context.marketData.data?.latestCandle?.close ?: return setup
            val atr = context.technicalAnalysis.data?.atr14
            val tolerance = if (atr != null) {
                atr.multiply(BigDecimal(MAX_ATR_MULTIPLE))
            } else {
                latestClose.multiply(MAX_PERCENT_DEVIATION)
            }
            val lowerBound = latestClose.subtract(tolerance)
            val upperBound = latestClose.add(tolerance)
            val plausible = setup.entryPrice in lowerBound..upperBound
            return if (plausible) setup else null
        }
    }
}
