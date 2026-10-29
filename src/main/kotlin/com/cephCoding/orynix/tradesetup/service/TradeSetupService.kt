package com.cephCoding.orynix.tradesetup.service

import com.cephCoding.orynix.agents.repository.AiTradeSetupProposalRepository
import com.cephCoding.orynix.backtesting.service.BacktestService
import com.cephCoding.orynix.common.exception.AiTradeSetupProposalNotFoundException
import com.cephCoding.orynix.common.exception.InvalidAccountCurrencyException
import com.cephCoding.orynix.common.exception.InvalidTradeSetupException
import com.cephCoding.orynix.common.exception.InvalidTradeSetupTransitionException
import com.cephCoding.orynix.common.exception.TradeSetupConflictException
import com.cephCoding.orynix.common.exception.TradeSetupNotFoundException
import com.cephCoding.orynix.instrument.InstrumentResolver
import com.cephCoding.orynix.instrument.domain.AssetType
import com.cephCoding.orynix.instrument.domain.Instrument
import com.cephCoding.orynix.papertrading.domain.OrderSide
import com.cephCoding.orynix.papertrading.domain.PaperOrder
import com.cephCoding.orynix.papertrading.domain.PositionEffect
import com.cephCoding.orynix.papertrading.dto.SubmitPaperOrderRequest
import com.cephCoding.orynix.papertrading.service.PaperTradingOrderService
import com.cephCoding.orynix.riskcalculator.dto.RiskCalculationRequest
import com.cephCoding.orynix.riskcalculator.service.RiskCalculatorService
import com.cephCoding.orynix.tradesetup.domain.TradeDirection
import com.cephCoding.orynix.tradesetup.domain.TradeSetup
import com.cephCoding.orynix.tradesetup.domain.TradeSetupDecision
import com.cephCoding.orynix.tradesetup.domain.TradeSetupRiskEvaluation
import com.cephCoding.orynix.tradesetup.domain.TradeSetupSource
import com.cephCoding.orynix.tradesetup.domain.TradeSetupStatus
import com.cephCoding.orynix.tradesetup.dto.TradeSetupBacktestLinkRequest
import com.cephCoding.orynix.tradesetup.dto.TradeSetupClosureRequest
import com.cephCoding.orynix.tradesetup.dto.TradeSetupCreateRequest
import com.cephCoding.orynix.tradesetup.dto.TradeSetupDecisionRequest
import com.cephCoding.orynix.tradesetup.dto.TradeSetupFromProposalRequest
import com.cephCoding.orynix.tradesetup.dto.TradeSetupPaperOrderRequest
import com.cephCoding.orynix.tradesetup.dto.TradeSetupRiskEvaluationRequest
import com.cephCoding.orynix.tradesetup.dto.TradeSetupUpdateRequest
import com.cephCoding.orynix.tradesetup.model.TradeSetupEntity
import com.cephCoding.orynix.tradesetup.repository.TradeSetupRepository
import com.cephCoding.orynix.tradingaccount.domain.TradingAccountProfile
import com.cephCoding.orynix.tradingaccount.service.TradingAccountService
import org.springframework.dao.DataIntegrityViolationException
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
    private val proposalRepository: AiTradeSetupProposalRepository,
    private val backtestService: BacktestService,
    private val paperTradingOrderService: PaperTradingOrderService,
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

    /**
     * Creates a TradeSetup from a server-persisted AiTradeSetupProposalEntity, making the AI
     * provenance server-verifiable (CLAUDE.md Section 21's Integration A) - every AI-provenance
     * field is copied from the proposal row looked up by id, never taken from the request. The
     * existing TradeSetupCreateRequest path (manual, or client-asserted AI_ANALYSIS) is completely
     * unchanged and still the only path that doesn't require a persisted proposal.
     *
     * Deliberately NOT @Transactional: there is exactly one write here, so the
     * DataIntegrityViolationException from the ai_proposal_id unique-constraint race (two
     * concurrent requests both passing the pre-check for the same proposal) must surface to the
     * catch below outside of any ongoing transaction - inside one, catching it would only mark the
     * transaction rollback-only, not let this method translate it and return normally.
     */
    fun createFromProposal(userId: UUID, request: TradeSetupFromProposalRequest): TradeSetup {
        val proposal = proposalRepository.findByIdAndUserId(request.aiProposalId, userId)
            ?: throw AiTradeSetupProposalNotFoundException(request.aiProposalId)

        val direction = TradeDirection.valueOf(proposal.direction)
        validatePriceOrdering(direction, proposal.entryPrice, proposal.stopLossPrice, proposal.takeProfitPrice)

        if (request.accountProfileId != null) {
            val instrument = InstrumentResolver.resolve(proposal.symbol)
            val profile = tradingAccountService.findOwnedProfile(userId, request.accountProfileId)
            validateAccountCurrency(instrument, profile)
        }

        val entity = TradeSetupEntity(
            userId = userId,
            assetType = proposal.assetType,
            symbol = proposal.symbol,
            direction = proposal.direction,
            entryPrice = proposal.entryPrice,
            stopLossPrice = proposal.stopLossPrice,
            targetPrice = proposal.takeProfitPrice,
            rationale = proposal.rationale,
            source = TradeSetupSource.AI_ANALYSIS.name,
            aiProviderName = proposal.providerName,
            aiModel = proposal.model,
            aiAnalysisAsOf = proposal.analysisAsOf,
            aiProposalId = proposal.id,
            accountProfileId = request.accountProfileId,
        )
        return try {
            repository.saveAndFlush(entity).toDomain()
        } catch (ex: DataIntegrityViolationException) {
            throw TradeSetupConflictException("This AI proposal has already been used to create a trade setup")
        }
    }

    /**
     * Associates an already-run backtest with this trade setup (CLAUDE.md Section 21's
     * Integration B) - a provenance link only. Rejects a symbol mismatch; never implies the
     * backtest's strategy-level historical performance validates this setup's specific entry/
     * stop/target levels, which BacktestEngine has no concept of. Reuses BacktestService's own
     * ownership-checked getBacktest (-> BacktestNotFoundException/404 identically for unknown or
     * cross-user ids) rather than reaching into backtesting's repository directly.
     */
    @Transactional
    fun linkBacktest(userId: UUID, id: UUID, request: TradeSetupBacktestLinkRequest): TradeSetup {
        val entity = findOwned(userId, id)
        val backtestRun = backtestService.getBacktest(userId, request.backtestRunId)
        if (backtestRun.symbol != entity.symbol) {
            throw InvalidTradeSetupException(
                "Backtest run symbol (${backtestRun.symbol}) does not match this trade setup's symbol (${entity.symbol})",
            )
        }
        entity.backtestRunId = backtestRun.id
        return repository.saveAndFlush(entity).toDomain()
    }

    /**
     * Initiates a real (simulated) paper-trading order from this ACCEPTED setup - "Practice in
     * Paper Trading" (CLAUDE.md Section 21's Integration C). Calls
     * PaperTradingOrderService.submitOrder exactly as-is - no new pricing/validation/idempotency/
     * locking logic is written here. SHORT setups are eligible exactly like LONG ones (paper
     * trading supports bounded shorts, unlike RiskCalculatorService) - this is never gated on
     * whether evaluateRisk could produce a sizing.
     *
     * Deliberately NOT @Transactional, for the same reason submitOrder itself isn't: external
     * price resolution happens inside submitOrder before it takes any DB lock, and wrapping this
     * orchestration in an outer transaction would hold this method's own (nonexistent) lock across
     * that call for no reason. The post-order link write re-reads the row fresh rather than reusing
     * the pre-order entity, since submitOrder can take a while and another concurrent call may have
     * already linked a different order in the meantime - this setup's existing link is reused and
     * reported back, never silently overwritten.
     */
    fun practiceInPaperTrading(userId: UUID, id: UUID, request: TradeSetupPaperOrderRequest): Pair<TradeSetup, PaperOrder> {
        val entity = findOwned(userId, id)
        requireStatus(entity, "practice a trade setup in paper trading", TradeSetupStatus.ACCEPTED)
        entity.paperOrderId?.let {
            throw TradeSetupConflictException(
                "This trade setup already has a linked paper order ($it); fetch it instead of submitting a new one",
            )
        }

        val direction = TradeDirection.valueOf(entity.direction)
        val side = when (direction) {
            TradeDirection.LONG -> OrderSide.BUY
            TradeDirection.SHORT -> OrderSide.SELL
        }
        val orderRequest = SubmitPaperOrderRequest(
            symbol = entity.symbol,
            side = side,
            positionEffect = PositionEffect.OPEN,
            quantity = request.quantity,
            idempotencyKey = request.idempotencyKey,
            stopLossPrice = entity.stopLossPrice,
            takeProfitPrice = entity.targetPrice,
        )
        val order = paperTradingOrderService.submitOrder(userId, request.paperAccountId, orderRequest)

        val fresh = repository.findByIdAndUserId(id, userId) ?: throw TradeSetupNotFoundException(id)
        if (fresh.paperOrderId != null) {
            return fresh.toDomain() to order
        }
        fresh.paperOrderId = order.id
        fresh.paperAccountId = request.paperAccountId
        return repository.saveAndFlush(fresh).toDomain() to order
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
