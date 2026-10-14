package com.cephCoding.orynix.riskcalculator.service

import com.cephCoding.orynix.backtesting.engine.RiskSizing
import com.cephCoding.orynix.common.exception.InvalidAccountCurrencyException
import com.cephCoding.orynix.common.exception.InvalidRiskCalculationException
import com.cephCoding.orynix.instrument.InstrumentResolver
import com.cephCoding.orynix.riskcalculator.domain.RiskCalculationResult
import com.cephCoding.orynix.riskcalculator.domain.RiskCalculationStatus
import com.cephCoding.orynix.riskcalculator.dto.RiskCalculationRequest
import com.cephCoding.orynix.tradingaccount.domain.TradingAccountProfile
import com.cephCoding.orynix.tradingaccount.service.TradingAccountService
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

/**
 * Standalone trade-setup risk calculator (CLAUDE.md Section 19) - never persists anything, never
 * calls a market-data/news/sentiment provider, and reuses RiskSizing (the same quantity-from-
 * risk-budget arithmetic BacktestEngine's ATR-based sizing uses) rather than duplicating it.
 * Long-only, matching backtesting's own scope: a setup where the stop is not below entry, or a
 * take-profit not above entry, is rejected outright as a malformed request, not silently
 * accepted or returned as an UNSUPPORTED result.
 */
@Service
class RiskCalculatorService(private val tradingAccountService: TradingAccountService) {

    fun calculate(userId: UUID, request: RiskCalculationRequest): RiskCalculationResult {
        val instrument = InstrumentResolver.resolve(request.symbol)
        val profile: TradingAccountProfile? = request.accountProfileId?.let {
            tradingAccountService.findOwnedProfile(userId, it)
        }

        val equity = request.equity ?: profile?.balance
            ?: throw InvalidRiskCalculationException("equity is required when accountProfileId is not supplied")
        if (equity.signum() <= 0) {
            throw InvalidRiskCalculationException("equity must be positive")
        }

        val riskPercent = request.riskPercentPerTrade ?: profile?.riskPercentPerTrade
            ?: throw InvalidRiskCalculationException("riskPercentPerTrade is required when accountProfileId is not supplied")
        if (riskPercent.signum() <= 0 || riskPercent > HUNDRED) {
            throw InvalidRiskCalculationException("riskPercentPerTrade must be between 0 and 100")
        }

        val accountCurrency = request.accountCurrency ?: profile?.accountCurrency
            ?: throw InvalidRiskCalculationException("accountCurrency is required when accountProfileId is not supplied")
        if (accountCurrency != instrument.requiredAccountCurrency) {
            throw InvalidAccountCurrencyException(
                "This instrument requires accountCurrency = ${instrument.requiredAccountCurrency}",
            )
        }

        if (request.stopLossPrice >= request.entryPrice) {
            throw InvalidRiskCalculationException("stopLossPrice must be below entryPrice for a long position")
        }
        if (request.takeProfitPrice != null && request.takeProfitPrice <= request.entryPrice) {
            throw InvalidRiskCalculationException("takeProfitPrice must be above entryPrice for a long position")
        }

        // Sizing is computed from the exact (unrounded) stop distance, matching BacktestEngine's
        // own ATR-based call into RiskSizing (riskPerUnit = initialStopMultiplier * atr, never
        // pre-rounded) - rounding to display scale before sizing would quietly bias the quantity
        // for instruments quoted to more than 4 decimals (most forex pairs). `stopDistance` in the
        // response is a rounded-for-display value; every downstream calculation (quantity,
        // estimatedLossAtStop, riskRewardRatio) uses the exact value instead.
        val exactStopDistance = request.entryPrice.subtract(request.stopLossPrice)
        val stopDistance = exactStopDistance.setScale(SCALE, RoundingMode.HALF_UP)
        val plannedMonetaryRisk = equity.multiply(riskPercent).divide(HUNDRED, SCALE, RoundingMode.HALF_UP)
        val quantity = RiskSizing.computeQuantity(equity, riskPercent, exactStopDistance)

        if (quantity == null) {
            return RiskCalculationResult(
                symbol = instrument.displaySymbol,
                assetType = instrument.assetType,
                accountCurrency = accountCurrency,
                equity = equity,
                riskPercentPerTrade = riskPercent,
                entryPrice = request.entryPrice,
                stopLossPrice = request.stopLossPrice,
                takeProfitPrice = request.takeProfitPrice,
                stopDistance = stopDistance,
                plannedMonetaryRisk = plannedMonetaryRisk,
                quantity = null,
                estimatedLossAtStop = null,
                estimatedGainAtTakeProfit = null,
                riskRewardRatio = null,
                status = RiskCalculationStatus.UNSUPPORTED,
                limitations = listOf(STANDARD_LIMITATION, UNSUPPORTED_LIMITATION),
            )
        }

        val estimatedLossAtStop = quantity.multiply(exactStopDistance).setScale(SCALE, RoundingMode.HALF_UP)
        val estimatedGainAtTakeProfit = request.takeProfitPrice?.let {
            quantity.multiply(it.subtract(request.entryPrice)).setScale(SCALE, RoundingMode.HALF_UP)
        }
        val riskRewardRatio = request.takeProfitPrice?.let {
            it.subtract(request.entryPrice).divide(exactStopDistance, SCALE, RoundingMode.HALF_UP)
        }

        return RiskCalculationResult(
            symbol = instrument.displaySymbol,
            assetType = instrument.assetType,
            accountCurrency = accountCurrency,
            equity = equity,
            riskPercentPerTrade = riskPercent,
            entryPrice = request.entryPrice,
            stopLossPrice = request.stopLossPrice,
            takeProfitPrice = request.takeProfitPrice,
            stopDistance = stopDistance,
            plannedMonetaryRisk = plannedMonetaryRisk,
            quantity = quantity,
            estimatedLossAtStop = estimatedLossAtStop,
            estimatedGainAtTakeProfit = estimatedGainAtTakeProfit,
            riskRewardRatio = riskRewardRatio,
            status = RiskCalculationStatus.OK,
            limitations = listOf(STANDARD_LIMITATION),
        )
    }

    companion object {
        private val HUNDRED = BigDecimal(100)
        private const val SCALE = 4
        private const val STANDARD_LIMITATION =
            "Does not model commission, slippage, margin, leverage, or currency conversion. " +
                "estimatedLossAtStop/estimatedGainAtTakeProfit assume an exact fill at the given prices."
        private const val UNSUPPORTED_LIMITATION =
            "Could not compute a usable quantity: the risk budget (equity x riskPercentPerTrade) is " +
                "smaller than the monetary risk of a single unit at this stop distance."
    }
}
