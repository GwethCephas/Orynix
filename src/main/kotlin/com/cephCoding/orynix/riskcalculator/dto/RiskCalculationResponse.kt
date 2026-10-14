package com.cephCoding.orynix.riskcalculator.dto

import com.cephCoding.orynix.instrument.domain.AssetType
import com.cephCoding.orynix.riskcalculator.domain.RiskCalculationResult
import com.cephCoding.orynix.riskcalculator.domain.RiskCalculationStatus
import java.math.BigDecimal

data class RiskCalculationResponse(
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
    val quantity: BigDecimal?,
    val estimatedLossAtStop: BigDecimal?,
    val estimatedGainAtTakeProfit: BigDecimal?,
    val riskRewardRatio: BigDecimal?,
    val status: RiskCalculationStatus,
    val limitations: List<String>,
) {
    companion object {
        fun from(result: RiskCalculationResult) = RiskCalculationResponse(
            symbol = result.symbol,
            assetType = result.assetType,
            accountCurrency = result.accountCurrency,
            equity = result.equity,
            riskPercentPerTrade = result.riskPercentPerTrade,
            entryPrice = result.entryPrice,
            stopLossPrice = result.stopLossPrice,
            takeProfitPrice = result.takeProfitPrice,
            stopDistance = result.stopDistance,
            plannedMonetaryRisk = result.plannedMonetaryRisk,
            quantity = result.quantity,
            estimatedLossAtStop = result.estimatedLossAtStop,
            estimatedGainAtTakeProfit = result.estimatedGainAtTakeProfit,
            riskRewardRatio = result.riskRewardRatio,
            status = result.status,
            limitations = result.limitations,
        )
    }
}
