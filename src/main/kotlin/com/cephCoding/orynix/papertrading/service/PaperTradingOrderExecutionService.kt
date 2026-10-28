package com.cephCoding.orynix.papertrading.service

import com.cephCoding.orynix.common.exception.InsufficientBuyingPowerException
import com.cephCoding.orynix.common.exception.InvalidAccountCurrencyException
import com.cephCoding.orynix.common.exception.InvalidPaperOrderException
import com.cephCoding.orynix.common.exception.InvalidPaperPositionTransitionException
import com.cephCoding.orynix.common.exception.PaperTradingAccountNotFoundException
import com.cephCoding.orynix.instrument.domain.Instrument
import com.cephCoding.orynix.papertrading.domain.AccountStatus
import com.cephCoding.orynix.papertrading.domain.OrderSide
import com.cephCoding.orynix.papertrading.domain.PaperOrder
import com.cephCoding.orynix.papertrading.domain.PositionDirection
import com.cephCoding.orynix.papertrading.domain.PositionEffect
import com.cephCoding.orynix.papertrading.domain.PositionStatus
import com.cephCoding.orynix.papertrading.domain.ResolvedPrice
import com.cephCoding.orynix.papertrading.dto.SubmitPaperOrderRequest
import com.cephCoding.orynix.papertrading.engine.AffordabilityCheck
import com.cephCoding.orynix.papertrading.engine.PaperTradingAccounting
import com.cephCoding.orynix.papertrading.model.PaperLedgerEntryEntity
import com.cephCoding.orynix.papertrading.model.PaperOrderEntity
import com.cephCoding.orynix.papertrading.model.PaperPositionEntity
import com.cephCoding.orynix.papertrading.model.PaperTradingAccountEntity
import com.cephCoding.orynix.papertrading.domain.LedgerEntryType
import com.cephCoding.orynix.papertrading.repository.PaperLedgerEntryRepository
import com.cephCoding.orynix.papertrading.repository.PaperOrderRepository
import com.cephCoding.orynix.papertrading.repository.PaperPositionRepository
import com.cephCoding.orynix.papertrading.repository.PaperTradingAccountRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.util.UUID

/**
 * The single locked, transactional core of order execution - deliberately a separate Spring bean
 * from PaperTradingOrderService (not just a private method on it), because @Transactional only
 * takes effect through Spring's AOP proxy: a self-invoked call from within the same class would
 * silently skip the transaction entirely. See CLAUDE.md Section 22 for the full design.
 *
 * `otherShortsCollateral` is resolved by the caller BEFORE this method's transaction/lock even
 * starts (price resolution must never happen while holding a DB lock) and is a pre-lock snapshot
 * of every OTHER open short position's mark-to-market collateral on this account - a narrow,
 * documented race exists only when two orders each opening a NEW short on DIFFERENT symbols in
 * the same account land at the same instant (see CLAUDE.md's known-limitations list); the much
 * more common same-symbol / cash-only races are fully serialized by the account lock below,
 * regardless.
 */
@Service
class PaperTradingOrderExecutionService(
    private val accountRepository: PaperTradingAccountRepository,
    private val positionRepository: PaperPositionRepository,
    private val orderRepository: PaperOrderRepository,
    private val ledgerRepository: PaperLedgerEntryRepository,
    private val properties: PaperTradingProperties,
) {
    private data class OpenCloseOutcome(
        val position: PaperPositionEntity,
        val cashDelta: BigDecimal,
        val filledQuantity: BigDecimal,
    )

    @Transactional
    fun execute(
        userId: UUID,
        accountId: UUID,
        instrument: Instrument,
        request: SubmitPaperOrderRequest,
        resolvedPrice: ResolvedPrice,
        executionAssumptions: String,
        otherShortsCollateral: BigDecimal,
    ): PaperOrder {
        // Re-check under the about-to-be-acquired lock - closes the race window left open by the
        // caller's own non-transactional fast-path check.
        orderRepository.findByAccountIdAndIdempotencyKey(accountId, request.idempotencyKey)?.let { return it.toDomain() }

        val account = accountRepository.findByIdAndUserIdForUpdate(accountId, userId)
            ?: throw PaperTradingAccountNotFoundException(accountId)
        if (account.status != AccountStatus.ACTIVE.name) {
            throw PaperTradingAccountNotFoundException(accountId)
        }
        if (account.baseCurrency != instrument.requiredAccountCurrency) {
            throw InvalidAccountCurrencyException(
                "Account currency ${account.baseCurrency} does not match the required ${instrument.requiredAccountCurrency} for ${instrument.displaySymbol}",
            )
        }

        val existingPosition = positionRepository.findByAccountIdAndSymbolAndStatus(
            accountId,
            instrument.displaySymbol,
            PositionStatus.OPEN.name,
        )
        val commission = account.commissionPerOrder
        val fillPrice = PaperTradingAccounting.applySlippage(resolvedPrice.price, account.slippageBps, request.side)

        val outcome = when (request.positionEffect) {
            PositionEffect.OPEN -> handleOpen(account, existingPosition, instrument, request, fillPrice, commission, otherShortsCollateral)
            PositionEffect.CLOSE -> handleClose(existingPosition, instrument, request, fillPrice, commission)
        }

        account.cash = account.cash.add(outcome.cashDelta)
        val savedAccount = accountRepository.saveAndFlush(account)
        val savedPosition = positionRepository.saveAndFlush(outcome.position)

        val order = PaperOrderEntity(
            accountId = accountId,
            positionId = savedPosition.id,
            symbol = instrument.displaySymbol,
            assetType = instrument.assetType.name,
            side = request.side.name,
            positionEffect = request.positionEffect.name,
            requestedQuantity = request.quantity,
            filledQuantity = outcome.filledQuantity,
            fillPrice = fillPrice,
            commission = commission,
            sourceCandleTimestamp = resolvedPrice.candleTimestamp,
            executionTimestamp = Instant.now(),
            executionAssumptions = executionAssumptions,
            idempotencyKey = request.idempotencyKey,
        )
        val savedOrder = orderRepository.saveAndFlush(order)

        val ledgerEntry = PaperLedgerEntryEntity(
            accountId = accountId,
            orderId = savedOrder.id,
            entryType = LedgerEntryType.ORDER_FILL.name,
            cashDelta = outcome.cashDelta,
            cashBalanceAfter = savedAccount.cash,
            description = "${request.side} ${request.positionEffect} ${outcome.filledQuantity} ${instrument.displaySymbol} @ $fillPrice",
        )
        ledgerRepository.saveAndFlush(ledgerEntry)

        return savedOrder.toDomain()
    }

    private fun handleOpen(
        account: PaperTradingAccountEntity,
        existing: PaperPositionEntity?,
        instrument: Instrument,
        request: SubmitPaperOrderRequest,
        fillPrice: BigDecimal,
        commission: BigDecimal,
        otherShortsCollateral: BigDecimal,
    ): OpenCloseOutcome {
        val newDirection = if (request.side == OrderSide.BUY) PositionDirection.LONG else PositionDirection.SHORT
        if (existing != null && PositionDirection.valueOf(existing.direction) != newDirection) {
            throw InvalidPaperPositionTransitionException(
                "An open ${existing.direction} position already exists for ${instrument.displaySymbol}; close it before opening the opposite direction",
            )
        }

        val existingQty = existing?.openQuantity ?: BigDecimal.ZERO
        val existingAvg = existing?.avgEntryPrice ?: BigDecimal.ZERO

        val fill = when (newDirection) {
            PositionDirection.LONG ->
                PaperTradingAccounting.computeOpenLongFill(existingQty, existingAvg, request.quantity, fillPrice, commission)
            PositionDirection.SHORT ->
                PaperTradingAccounting.computeOpenShortFill(existingQty, existingAvg, request.quantity, fillPrice, commission)
        }

        val required = when (newDirection) {
            PositionDirection.LONG -> PaperTradingAccounting.requiredBuyingPowerForOpenLong(request.quantity, fillPrice, commission)
            PositionDirection.SHORT -> PaperTradingAccounting.requiredBuyingPowerForOpenShort(
                request.quantity,
                fillPrice,
                commission,
                properties.shortCollateralMultiplier,
            )
        }
        val buyingPowerBeforeThisOrder = PaperTradingAccounting.computeBuyingPower(account.cash, otherShortsCollateral)
        val affordability = AffordabilityCheck.check(buyingPowerBeforeThisOrder, required)
        if (!affordability.sufficient) {
            throw InsufficientBuyingPowerException(affordability.shortfall, account.baseCurrency)
        }

        val position = existing ?: PaperPositionEntity(
            accountId = account.id,
            symbol = instrument.displaySymbol,
            assetType = instrument.assetType.name,
            direction = newDirection.name,
            status = PositionStatus.OPEN.name,
            openQuantity = zeroQuantity(),
            avgEntryPrice = zeroMoney(),
            totalOpenCommissionPaid = zeroMoney(),
            realizedPnl = zeroMoney(),
            stopLossPrice = null,
            takeProfitPrice = null,
            openedAt = Instant.now(),
        )
        position.openQuantity = fill.newOpenQuantity
        position.avgEntryPrice = fill.newAvgEntryPrice
        position.totalOpenCommissionPaid = position.totalOpenCommissionPaid.add(commission)
        validateProtectiveLevels(newDirection, position.avgEntryPrice, request.stopLossPrice, request.takeProfitPrice)
        if (request.stopLossPrice != null) position.stopLossPrice = request.stopLossPrice.setScale(PaperTradingAccounting.MONEY_SCALE, RoundingMode.HALF_UP)
        if (request.takeProfitPrice != null) position.takeProfitPrice = request.takeProfitPrice.setScale(PaperTradingAccounting.MONEY_SCALE, RoundingMode.HALF_UP)

        return OpenCloseOutcome(position, fill.cashDelta, request.quantity)
    }

    private fun handleClose(
        existing: PaperPositionEntity?,
        instrument: Instrument,
        request: SubmitPaperOrderRequest,
        fillPrice: BigDecimal,
        commission: BigDecimal,
    ): OpenCloseOutcome {
        if (existing == null) {
            throw InvalidPaperPositionTransitionException("No open position exists for ${instrument.displaySymbol} to close")
        }
        val direction = PositionDirection.valueOf(existing.direction)
        val expectedSide = if (direction == PositionDirection.LONG) OrderSide.SELL else OrderSide.BUY
        if (request.side != expectedSide) {
            throw InvalidPaperPositionTransitionException(
                "Closing a $direction position on ${instrument.displaySymbol} requires side=$expectedSide, got ${request.side}",
            )
        }
        if (request.quantity.signum() <= 0 || request.quantity > existing.openQuantity) {
            throw InvalidPaperOrderException("quantity must be in (0, ${existing.openQuantity}] to close this position")
        }

        val wasFullyClosed = PaperTradingAccounting.isFullyClosed(existing.openQuantity, request.quantity)
        val fill = when (direction) {
            PositionDirection.LONG ->
                PaperTradingAccounting.computeCloseLongFill(existing.openQuantity, existing.avgEntryPrice, request.quantity, fillPrice, commission)
            PositionDirection.SHORT ->
                PaperTradingAccounting.computeCloseShortFill(existing.openQuantity, existing.avgEntryPrice, request.quantity, fillPrice, commission)
        }

        existing.openQuantity = fill.newOpenQuantity
        existing.realizedPnl = existing.realizedPnl.add(fill.realizedPnl)
        if (wasFullyClosed) {
            existing.status = PositionStatus.CLOSED.name
            existing.closedAt = Instant.now()
        }

        return OpenCloseOutcome(existing, fill.cashDelta, request.quantity)
    }

    private fun validateProtectiveLevels(
        direction: PositionDirection,
        entryPrice: BigDecimal,
        stopLossPrice: BigDecimal?,
        takeProfitPrice: BigDecimal?,
    ) {
        when (direction) {
            PositionDirection.LONG -> {
                if (stopLossPrice != null && stopLossPrice >= entryPrice) {
                    throw InvalidPaperOrderException("stopLossPrice must be below the entry price for a long position")
                }
                if (takeProfitPrice != null && takeProfitPrice <= entryPrice) {
                    throw InvalidPaperOrderException("takeProfitPrice must be above the entry price for a long position")
                }
            }
            PositionDirection.SHORT -> {
                if (stopLossPrice != null && stopLossPrice <= entryPrice) {
                    throw InvalidPaperOrderException("stopLossPrice must be above the entry price for a short position")
                }
                if (takeProfitPrice != null && takeProfitPrice >= entryPrice) {
                    throw InvalidPaperOrderException("takeProfitPrice must be below the entry price for a short position")
                }
            }
        }
    }

    // BigDecimal.ZERO itself has scale 0 - starting a brand-new position from these instead
    // keeps every field at the schema's real scale from construction, not just after the first
    // arithmetic operation touches it (which would mask the same class of bug the account-level
    // fix above addresses - see CLAUDE.md Section 22).
    private fun zeroMoney(): BigDecimal = BigDecimal.ZERO.setScale(PaperTradingAccounting.MONEY_SCALE, RoundingMode.HALF_UP)

    private fun zeroQuantity(): BigDecimal = BigDecimal.ZERO.setScale(PaperTradingAccounting.QUANTITY_SCALE, RoundingMode.HALF_UP)
}
