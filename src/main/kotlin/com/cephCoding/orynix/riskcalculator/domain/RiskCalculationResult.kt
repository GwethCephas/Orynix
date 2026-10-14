package com.cephCoding.orynix.riskcalculator.domain

import com.cephCoding.orynix.instrument.domain.AssetType
import java.math.BigDecimal

/**
 * UNSUPPORTED means the inputs were well-formed but a usable quantity could not be derived (the
 * risk budget is smaller than one unit's monetary risk at this stop distance) - quantity and
 * every value that depends on it are null in that case, never fabricated as zero. A logically
 * invalid long setup (stop at or above entry, take-profit at or below entry) or an account
 * currency that does not match the instrument is rejected outright (400) before a result is ever
 * built - see RiskCalculatorService - so UNSUPPORTED is reserved for this one genuinely
 * reachable "well-formed but not sizeable" case.
 */
enum class RiskCalculationStatus { OK, UNSUPPORTED }

/**
 * Never carries a BUY/SELL recommendation or any directional call - purely the arithmetic
 * consequence of the inputs given, long-only (CLAUDE.md Section 19, matching backtesting's
 * long-only scope). estimatedLossAtStop/estimatedGainAtTakeProfit assume an exact fill at the
 * given prices - no commission, slippage, margin, leverage, or currency conversion is modeled,
 * always restated in `limitations`, never only in documentation.
 */
data class RiskCalculationResult(
    val symbol: String,
    val assetType: AssetType,
    val accountCurrency: String,
    val equity: BigDecimal,
    val riskPercentPerTrade: BigDecimal,
    val entryPrice: BigDecimal,
    val stopLossPrice: BigDecimal,
    val takeProfitPrice: BigDecimal?,
    val stopDistance: BigDecimal,
    val plannedMonetaryRisk: BigDecimal,
    // Null together, always, when status is UNSUPPORTED - never a fabricated zero.
    val quantity: BigDecimal?,
    val estimatedLossAtStop: BigDecimal?,
    val estimatedGainAtTakeProfit: BigDecimal?,
    val riskRewardRatio: BigDecimal?,
    val status: RiskCalculationStatus,
    val limitations: List<String>,
)
