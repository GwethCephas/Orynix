package com.cephCoding.orynix.tradesetup.service

import com.cephCoding.orynix.common.exception.InvalidAccountCurrencyException
import com.cephCoding.orynix.common.exception.InvalidTradeSetupException
import com.cephCoding.orynix.common.exception.InvalidTradeSetupTransitionException
import com.cephCoding.orynix.common.exception.TradeSetupNotFoundException
import com.cephCoding.orynix.instrument.InstrumentResolver
import com.cephCoding.orynix.instrument.domain.AssetType
import com.cephCoding.orynix.instrument.domain.Instrument
import com.cephCoding.orynix.riskcalculator.dto.RiskCalculationRequest
import com.cephCoding.orynix.riskcalculator.service.RiskCalculatorService
import com.cephCoding.orynix.tradesetup.domain.TradeDirection
import com.cephCoding.orynix.tradesetup.domain.TradeSetup
import com.cephCoding.orynix.tradesetup.domain.TradeSetupDecision
import com.cephCoding.orynix.tradesetup.domain.TradeSetupRiskEvaluation
import com.cephCoding.orynix.tradesetup.domain.TradeSetupSource
import com.cephCoding.orynix.tradesetup.domain.TradeSetupStatus
import com.cephCoding.orynix.tradesetup.dto.TradeSetupClosureRequest
import com.cephCoding.orynix.tradesetup.dto.TradeSetupCreateRequest
import com.cephCoding.orynix.tradesetup.dto.TradeSetupDecisionRequest
import com.cephCoding.orynix.tradesetup.dto.TradeSetupRiskEvaluationRequest
import com.cephCoding.orynix.tradesetup.dto.TradeSetupUpdateRequest
import com.cephCoding.orynix.tradesetup.model.TradeSetupEntity
import com.cephCoding.orynix.tradesetup.repository.TradeSetupRepository
import com.cephCoding.orynix.tradingaccount.domain.TradingAccountProfile
import com.cephCoding.orynix.tradingaccount.service.TradingAccountService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Persistent trade-idea workflow (CLAUDE.md Section 21) - CRUD + lifecycle + on-demand risk
 * evaluation, reusing RiskCalculatorService/TradingAccountService/InstrumentResolver as-is rather
 * than duplicating any of their logic. ACCEPTED means the user accepted the idea for
 * consideration, never that a trade was executed; CLOSED means the user reported it closed, never
 * that a broker confirmed it. aiProviderName/aiModel/aiAnalysisAsOf are client-asserted and
 * server-unverifiable - the AI Market Analysis Agent persists nothing and has no stable analysis
 * id to check a create request against.
 */
@Service
class TradeSetupService(
    private val repository: TradeSetupRepository,
    private val tradingAccountService: TradingAccountService,
    private val riskCalculatorService: RiskCalculatorService,
) {

    @Transactional
    fun create(userId: UUID, request: TradeSetupCreateRequest): TradeSetup {
        validateAiProvenance(request.source, request.aiProviderName, request.aiModel, request.aiAnalysisAsOf)
        validatePriceOrdering(request.direction, request.entryPrice, request.stopLossPrice, request.targetPrice)
        val instrument = InstrumentResolver.resolve(request.symbol)

        if (request.accountProfileId != null) {
            val profile = tradingAccountService.findOwnedProfile(userId, request.accountProfileId)
            validateAccountCurrency(instrument, profile)
        }

        val entity = TradeSetupEntity(
            userId = userId,
            assetType = instrument.assetType.name,
            symbol = instrument.displaySymbol,
            direction = request.direction.name,
            entryPrice = request.entryPrice,
            stopLossPrice = request.stopLossPrice,
            targetPrice = request.targetPrice,
            rationale = request.rationale,
            source = request.source.name,
            aiProviderName = request.aiProviderName,
            aiModel = request.aiModel,
            aiAnalysisAsOf = request.aiAnalysisAsOf,
            accountProfileId = request.accountProfileId,
        )
        return repository.saveAndFlush(entity).toDomain()
    }

    fun list(userId: UUID, status: TradeSetupStatus?, assetType: AssetType?, symbol: String?): List<TradeSetup> =
        repository.findAllByUserIdWithFilters(userId, status?.name, assetType?.name, symbol?.trim()?.uppercase())
            .map { it.toDomain() }

    fun get(userId: UUID, id: UUID): TradeSetup = findOwned(userId, id).toDomain()

    @Transactional
    fun update(userId: UUID, id: UUID, request: TradeSetupUpdateRequest): TradeSetup {
        val entity = findOwned(userId, id)
        requireStatus(entity, "edit a trade setup", TradeSetupStatus.DRAFT)

        val direction = TradeDirection.valueOf(entity.direction)
        validatePriceOrdering(direction, request.entryPrice, request.stopLossPrice, request.targetPrice)

        if (request.accountProfileId != null) {
            val instrument = InstrumentResolver.resolve(entity.symbol)
            val profile = tradingAccountService.findOwnedProfile(userId, request.accountProfileId)
            validateAccountCurrency(instrument, profile)
        }

        entity.entryPrice = request.entryPrice
        entity.stopLossPrice = request.stopLossPrice
        entity.targetPrice = request.targetPrice
        entity.rationale = request.rationale
        entity.accountProfileId = request.accountProfileId
        return repository.saveAndFlush(entity).toDomain()
    }

    @Transactional
    fun expire(userId: UUID, id: UUID) {
        val entity = findOwned(userId, id)
        requireStatus(entity, "expire a trade setup", TradeSetupStatus.DRAFT)
        entity.status = TradeSetupStatus.EXPIRED.name
        repository.saveAndFlush(entity)
    }

    @Transactional
    fun decide(userId: UUID, id: UUID, request: TradeSetupDecisionRequest): TradeSetup {
        val entity = findOwned(userId, id)
        requireStatus(entity, "record a decision", TradeSetupStatus.DRAFT)
        entity.status = when (request.decision) {
            TradeSetupDecision.ACCEPT -> TradeSetupStatus.ACCEPTED
            TradeSetupDecision.REJECT -> TradeSetupStatus.REJECTED
        }.name
        entity.decisionNotes = request.notes
        entity.decidedAt = Instant.now()
        return repository.saveAndFlush(entity).toDomain()
    }

    @Transactional
    fun close(userId: UUID, id: UUID, request: TradeSetupClosureRequest): TradeSetup {
        val entity = findOwned(userId, id)
        requireStatus(entity, "close a trade setup", TradeSetupStatus.ACCEPTED)
        entity.status = TradeSetupStatus.CLOSED.name
        entity.exitPrice = request.exitPrice
        entity.outcome = request.outcome?.name
        entity.closureNotes = request.closureNotes
        entity.closedAt = Instant.now()
        return repository.saveAndFlush(entity).toDomain()
    }

    /**
     * Always ephemeral - nothing here is ever written back onto the setup row, so a result can
     * never be presented as current when it might be stale. Mirrors
     * MarketAnalysisService.attachSizing's branching exactly: SHORT and missing-stop-loss both
     * skip RiskCalculatorService entirely rather than calling it with inputs it can't handle
     * (it's strictly long-only and has no concept of an absent stop). Not status-gated - valid
     * at any lifecycle state, including CLOSED, for post-mortem review.
     */
    fun evaluateRisk(userId: UUID, id: UUID, request: TradeSetupRiskEvaluationRequest): TradeSetupRiskEvaluation {
        val entity = findOwned(userId, id)
        val direction = TradeDirection.valueOf(entity.direction)

        if (direction == TradeDirection.SHORT) {
            return TradeSetupRiskEvaluation(sizing = null, limitations = listOf(MSG_SHORT_UNSUPPORTED))
        }
        val stopLossPrice = entity.stopLossPrice
            ?: return TradeSetupRiskEvaluation(sizing = null, limitations = listOf(MSG_STOP_LOSS_REQUIRED))

        val result = riskCalculatorService.calculate(
            userId,
            RiskCalculationRequest(
                symbol = entity.symbol,
                accountProfileId = request.accountProfileId ?: entity.accountProfileId,
                equity = request.equity,
                accountCurrency = request.accountCurrency,
                riskPercentPerTrade = request.riskPercentPerTrade,
                entryPrice = entity.entryPrice,
                stopLossPrice = stopLossPrice,
                takeProfitPrice = entity.targetPrice,
            ),
        )
        return TradeSetupRiskEvaluation(sizing = result, limitations = result.limitations)
    }

    private fun findOwned(userId: UUID, id: UUID): TradeSetupEntity =
        repository.findByIdAndUserId(id, userId) ?: throw TradeSetupNotFoundException(id)

    private fun requireStatus(entity: TradeSetupEntity, action: String, vararg allowed: TradeSetupStatus) {
        val current = TradeSetupStatus.valueOf(entity.status)
        if (current !in allowed) {
            throw InvalidTradeSetupTransitionException(
                "Cannot $action: trade setup status is $current, must be one of ${allowed.joinToString()}",
            )
        }
    }

    private fun validateAccountCurrency(instrument: Instrument, profile: TradingAccountProfile) {
        if (profile.accountCurrency != instrument.requiredAccountCurrency) {
            throw InvalidAccountCurrencyException(
                "Trading account profile currency (${profile.accountCurrency}) does not match this " +
                    "trade setup's required accountCurrency (${instrument.requiredAccountCurrency})",
            )
        }
    }

    companion object {
        private const val MSG_SHORT_UNSUPPORTED = "Deterministic position sizing is currently long-only"
        private const val MSG_STOP_LOSS_REQUIRED = "A stop-loss price is required to evaluate risk"

        private fun validateAiProvenance(
            source: TradeSetupSource,
            aiProviderName: String?,
            aiModel: String?,
            aiAnalysisAsOf: Instant?,
        ) {
            val anyPresent = aiProviderName != null || aiModel != null || aiAnalysisAsOf != null
            val allPresent = aiProviderName != null && aiModel != null && aiAnalysisAsOf != null
            when (source) {
                TradeSetupSource.AI_ANALYSIS -> if (!allPresent) {
                    throw InvalidTradeSetupException(
                        "aiProviderName, aiModel, and aiAnalysisAsOf are all required when source is AI_ANALYSIS",
                    )
                }
                TradeSetupSource.MANUAL -> if (anyPresent) {
                    throw InvalidTradeSetupException(
                        "aiProviderName, aiModel, and aiAnalysisAsOf must not be supplied when source is MANUAL",
                    )
                }
            }
        }

        /**
         * Testable without constructing the full service (matches
         * BacktestService.computeWarmupFromDate / MarketAnalysisService.validateAgainstMarket
         * precedent). Each of stopLossPrice/targetPrice imposes no ordering constraint when
         * absent - optional fields really are optional, per the spec's explicit "support optional
         * stop-loss and target fields" requirement.
         */
        internal fun validatePriceOrdering(
            direction: TradeDirection,
            entryPrice: BigDecimal,
            stopLossPrice: BigDecimal?,
            targetPrice: BigDecimal?,
        ) {
            when (direction) {
                TradeDirection.LONG -> {
                    if (stopLossPrice != null && stopLossPrice >= entryPrice) {
                        throw InvalidTradeSetupException("stopLossPrice must be below entryPrice for a long position")
                    }
                    if (targetPrice != null && targetPrice <= entryPrice) {
                        throw InvalidTradeSetupException("targetPrice must be above entryPrice for a long position")
                    }
                }
                TradeDirection.SHORT -> {
                    if (stopLossPrice != null && stopLossPrice <= entryPrice) {
                        throw InvalidTradeSetupException("stopLossPrice must be above entryPrice for a short position")
                    }
                    if (targetPrice != null && targetPrice >= entryPrice) {
                        throw InvalidTradeSetupException("targetPrice must be below entryPrice for a short position")
                    }
                }
            }
        }
    }
}
