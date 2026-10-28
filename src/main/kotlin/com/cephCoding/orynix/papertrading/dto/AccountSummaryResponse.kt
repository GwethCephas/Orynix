package com.cephCoding.orynix.papertrading.dto

import com.cephCoding.orynix.papertrading.domain.AccountSummary
import java.math.BigDecimal

data class AccountSummaryResponse(
    val cash: BigDecimal,
    val equity: BigDecimal,
    val buyingPower: BigDecimal,
    val marketValueLong: BigDecimal,
    val totalReservedCollateral: BigDecimal,
    val unrealizedPnlLong: BigDecimal,
    val unrealizedPnlShort: BigDecimal,
    val realizedPnlCumulative: BigDecimal,
    val lifetimeCommissionPaid: BigDecimal,
    val valuationWarnings: List<String>,
) {
    companion object {
        fun from(summary: AccountSummary) = AccountSummaryResponse(
            cash = summary.cash,
            equity = summary.equity,
            buyingPower = summary.buyingPower,
            marketValueLong = summary.marketValueLong,
            totalReservedCollateral = summary.totalReservedCollateral,
            unrealizedPnlLong = summary.unrealizedPnlLong,
            unrealizedPnlShort = summary.unrealizedPnlShort,
            realizedPnlCumulative = summary.realizedPnlCumulative,
            lifetimeCommissionPaid = summary.lifetimeCommissionPaid,
            valuationWarnings = summary.valuationWarnings,
        )
    }
}
