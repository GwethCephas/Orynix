package com.cephCoding.orynix.papertrading.service

import com.cephCoding.orynix.common.exception.InvalidPaperOrderException
import com.cephCoding.orynix.common.exception.InvalidPaperPositionTransitionException
import com.cephCoding.orynix.common.exception.PaperPositionNotFoundException
import com.cephCoding.orynix.instrument.InstrumentResolver
import com.cephCoding.orynix.instrument.domain.Instrument
import com.cephCoding.orynix.papertrading.domain.AccountSummary
import com.cephCoding.orynix.papertrading.domain.OrderSide
import com.cephCoding.orynix.papertrading.domain.PaperOrder
import com.cephCoding.orynix.papertrading.domain.PaperPosition
import com.cephCoding.orynix.papertrading.domain.PositionDirection
import com.cephCoding.orynix.papertrading.domain.PositionEffect
import com.cephCoding.orynix.papertrading.domain.PositionStatus
import com.cephCoding.orynix.papertrading.domain.PositionValuation
import com.cephCoding.orynix.papertrading.domain.PriceStatus
import com.cephCoding.orynix.papertrading.dto.ClosePositionRequest
import com.cephCoding.orynix.papertrading.dto.SubmitPaperOrderRequest
import com.cephCoding.orynix.papertrading.engine.PaperTradingAccounting
import com.cephCoding.orynix.papertrading.model.PaperPositionEntity
import com.cephCoding.orynix.papertrading.repository.PaperLedgerEntryRepository
import com.cephCoding.orynix.papertrading.repository.PaperOrderRepository
import com.cephCoding.orynix.papertrading.repository.PaperPositionRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

/**
 * Orchestrates order submission (idempotency pre-check, instrument/price resolution outside any
 * transaction, delegation to the locked PaperTradingOrderExecutionService) and every read-side
 * operation (summary, positions, orders, ledger). See CLAUDE.md Section 22 for the full design,
 * including why execution is a separate bean rather than a private method here.
 */
@Service
class PaperTradingOrderService(
    private val accountService: PaperTradingAccountService,
    private val executionService: PaperTradingOrderExecutionService,
    private val positionRepository: PaperPositionRepository,
    private val orderRepository: PaperOrderRepository,
    private val ledgerRepository: PaperLedgerEntryRepository,
    private val priceResolver: PaperTradingPriceResolver,
    private val properties: PaperTradingProperties,
) {
    fun submitOrder(userId: UUID, accountId: UUID, request: SubmitPaperOrderRequest): PaperOrder {
        validateOrderShape(request)

        // Fast path - no lock, no transaction; covers almost every retry.
        orderRepository.findByAccountIdAndIdempotencyKey(accountId, request.idempotencyKey)?.let { return it.toDomain() }

        val instrument = InstrumentResolver.resolve(request.symbol)
        val resolvedPrice = priceResolver.resolveForOrder(instrument)
        val executionAssumptions = priceResolver.buildExecutionAssumptions(resolvedPrice)
        val otherShortsCollateral = computeOtherShortsCollateral(accountId, instrument.displaySymbol)

        return try {
            executionService.execute(userId, accountId, instrument, request, resolvedPrice, executionAssumptions, otherShortsCollateral)
        } catch (ex: DataIntegrityViolationException) {
            // The losing side of the rare race where two requests both missed the fast-path check
            // above - the failed transaction has already fully rolled back, so this is a clean
            // re-read, never a second fill.
            orderRepository.findByAccountIdAndIdempotencyKey(accountId, request.idempotencyKey)?.toDomain() ?: throw ex
        }
    }

    fun closePosition(userId: UUID, accountId: UUID, positionId: UUID, request: ClosePositionRequest): PaperOrder {
        // Idempotency check FIRST, before validating position state - a genuine replay of an
        // already-succeeded close (same key) must return that original order, not a spurious
        // "not open" error just because the close it's replaying already happened. Mirrors
        // submitOrder's own fast-path check; this is what makes closePosition's wrapping of
        // submitOrder genuinely idempotent end-to-end, not just non-duplicating.
        orderRepository.findByAccountIdAndIdempotencyKey(accountId, request.idempotencyKey)?.let { return it.toDomain() }

        val account = accountService.findOwnedActive(userId, accountId)
        val position = positionRepository.findByIdAndAccountId(positionId, account.id)
            ?: throw PaperPositionNotFoundException(positionId)
        if (position.status != PositionStatus.OPEN.name) {
            throw InvalidPaperPositionTransitionException("Position $positionId is not open")
        }

        val closeQuantity = request.quantity ?: position.openQuantity
        val direction = PositionDirection.valueOf(position.direction)
        val (side, effect) = when (direction) {
            PositionDirection.LONG -> OrderSide.SELL to PositionEffect.CLOSE
            PositionDirection.SHORT -> OrderSide.BUY to PositionEffect.CLOSE
        }

        val orderRequest = SubmitPaperOrderRequest(
            symbol = position.symbol,
            side = side,
            positionEffect = effect,
            quantity = closeQuantity,
            idempotencyKey = request.idempotencyKey,
            stopLossPrice = null,
            takeProfitPrice = null,
        )
        return submitOrder(userId, accountId, orderRequest)
    }

    fun getSummary(userId: UUID, accountId: UUID): AccountSummary {
        val account = accountService.findOwned(userId, accountId)
        val openPositions = positionRepository.findAllByAccountIdAndStatusOrderByOpenedAtDesc(accountId, PositionStatus.OPEN.name)

        var marketValueLong = zeroMoney()
        var totalReservedCollateral = zeroMoney()
        var unrealizedPnlLong = zeroMoney()
        var unrealizedPnlShort = zeroMoney()
        var realizedPnlCumulative = zeroMoney()
        val warnings = mutableListOf<String>()

        val allPositionsEverOpened = positionRepository.findAllByAccountIdAndStatusOrderByOpenedAtDesc(accountId, PositionStatus.CLOSED.name) + openPositions
        for (position in allPositionsEverOpened) {
            realizedPnlCumulative = realizedPnlCumulative.add(position.realizedPnl)
        }
        val lifetimeCommissionPaid = orderRepository.sumCommissionByAccountId(accountId)
            .setScale(PaperTradingAccounting.MONEY_SCALE, RoundingMode.HALF_UP)

        for (position in openPositions) {
            val instrument = InstrumentResolver.resolve(position.symbol)
            val (valuation, warning) = valuationFor(position, instrument)
            warning?.let { warnings.add(it) }
            when (PositionDirection.valueOf(position.direction)) {
                PositionDirection.LONG -> {
                    marketValueLong = marketValueLong.add(valuation.marketValue)
                    unrealizedPnlLong = unrealizedPnlLong.add(valuation.unrealizedPnl)
                }
                PositionDirection.SHORT -> {
                    totalReservedCollateral = totalReservedCollateral.add(requireNotNull(valuation.reservedCollateral))
                    unrealizedPnlShort = unrealizedPnlShort.add(valuation.unrealizedPnl)
                }
            }
        }

        val buyingPower = PaperTradingAccounting.computeBuyingPower(account.cash, totalReservedCollateral)
        val shortMarketValue = openPositions
            .filter { it.direction == PositionDirection.SHORT.name }
            .fold(zeroMoney()) { acc, pos ->
                val instrument = InstrumentResolver.resolve(pos.symbol)
                val mark = priceResolver.resolveLatest(instrument)?.price ?: pos.avgEntryPrice
                acc.add(PaperTradingAccounting.computeMarketValue(pos.openQuantity, mark))
            }
        val equity = PaperTradingAccounting.computeEquity(account.cash, marketValueLong, shortMarketValue)

        return AccountSummary(
            cash = account.cash,
            equity = equity,
            buyingPower = buyingPower,
            marketValueLong = marketValueLong,
            totalReservedCollateral = totalReservedCollateral,
            unrealizedPnlLong = unrealizedPnlLong,
            unrealizedPnlShort = unrealizedPnlShort,
            realizedPnlCumulative = realizedPnlCumulative,
            lifetimeCommissionPaid = lifetimeCommissionPaid,
            valuationWarnings = warnings,
        )
    }

    fun listOpenPositions(userId: UUID, accountId: UUID): List<Pair<PaperPosition, PositionValuation?>> {
        accountService.findOwned(userId, accountId)
        return positionRepository.findAllByAccountIdAndStatusOrderByOpenedAtDesc(accountId, PositionStatus.OPEN.name).map { position ->
            val instrument = InstrumentResolver.resolve(position.symbol)
            val (valuation, _) = valuationFor(position, instrument)
            position.toDomain() to valuation
        }
    }

    fun listClosedPositions(userId: UUID, accountId: UUID, pageable: Pageable): Page<PaperPosition> {
        accountService.findOwned(userId, accountId)
        return positionRepository.findAllByAccountIdAndStatusOrderByClosedAtDesc(accountId, PositionStatus.CLOSED.name, pageable)
            .map { it.toDomain() }
    }

    fun listOrders(userId: UUID, accountId: UUID, pageable: Pageable): Page<PaperOrder> {
        accountService.findOwned(userId, accountId)
        return orderRepository.findAllByAccountIdOrderByCreatedAtDesc(accountId, pageable).map { it.toDomain() }
    }

    fun listLedgerEntries(userId: UUID, accountId: UUID, pageable: Pageable) =
        run {
            accountService.findOwned(userId, accountId)
            ledgerRepository.findAllByAccountIdOrderByCreatedAtDesc(accountId, pageable).map { it.toDomain() }
        }

    private fun computeOtherShortsCollateral(accountId: UUID, excludingSymbol: String): BigDecimal {
        val otherOpenShorts = positionRepository.findAllByAccountIdAndStatusOrderByOpenedAtDesc(accountId, PositionStatus.OPEN.name)
            .filter { it.direction == PositionDirection.SHORT.name && it.symbol != excludingSymbol }
        return otherOpenShorts.fold(zeroMoney()) { acc, position ->
            val instrument = InstrumentResolver.resolve(position.symbol)
            val markPrice = priceResolver.resolveLatest(instrument)?.price ?: position.avgEntryPrice
            acc.add(PaperTradingAccounting.computeReservedCollateral(position.openQuantity, markPrice, properties.shortCollateralMultiplier))
        }
    }

    private fun valuationFor(position: PaperPositionEntity, instrument: Instrument): Pair<PositionValuation, String?> {
        val direction = PositionDirection.valueOf(position.direction)
        val resolved = priceResolver.resolveLatest(instrument)

        val mark = resolved?.price ?: position.avgEntryPrice
        val priceAsOf = resolved?.candleTimestamp ?: position.openedAt
        val priceStatus = when {
            resolved == null -> PriceStatus.UNAVAILABLE
            resolved.ageDays > properties.maxPriceStalenessDays -> PriceStatus.STALE
            else -> PriceStatus.OK
        }
        val warning = when (priceStatus) {
            PriceStatus.UNAVAILABLE -> "No price is available for ${position.symbol}"
            PriceStatus.STALE -> "Price for ${position.symbol} is ${resolved!!.ageDays} day(s) old"
            PriceStatus.OK -> null
        }

        val marketValue = PaperTradingAccounting.computeMarketValue(position.openQuantity, mark)
        val unrealizedPnl = when (direction) {
            PositionDirection.LONG -> PaperTradingAccounting.computeUnrealizedPnlLong(position.openQuantity, position.avgEntryPrice, mark)
            PositionDirection.SHORT -> PaperTradingAccounting.computeUnrealizedPnlShort(position.openQuantity, position.avgEntryPrice, mark)
        }
        val reservedCollateral = if (direction == PositionDirection.SHORT) {
            PaperTradingAccounting.computeReservedCollateral(position.openQuantity, mark, properties.shortCollateralMultiplier)
        } else {
            null
        }

        return PositionValuation(
            markPrice = mark,
            priceStatus = priceStatus,
            priceAsOf = priceAsOf,
            marketValue = marketValue,
            unrealizedPnl = unrealizedPnl,
            reservedCollateral = reservedCollateral,
        ) to warning
    }

    private fun validateOrderShape(request: SubmitPaperOrderRequest) {
        if (request.positionEffect == PositionEffect.CLOSE && (request.stopLossPrice != null || request.takeProfitPrice != null)) {
            throw InvalidPaperOrderException("stopLossPrice/takeProfitPrice do not apply to a CLOSE order")
        }
    }

    private fun zeroMoney(): BigDecimal = BigDecimal.ZERO.setScale(PaperTradingAccounting.MONEY_SCALE, RoundingMode.HALF_UP)
}
