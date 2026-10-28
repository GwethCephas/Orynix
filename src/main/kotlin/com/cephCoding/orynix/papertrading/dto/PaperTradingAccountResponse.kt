package com.cephCoding.orynix.papertrading.dto

import com.cephCoding.orynix.papertrading.domain.AccountStatus
import com.cephCoding.orynix.papertrading.domain.PaperTradingAccount
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class PaperTradingAccountResponse(
    val id: UUID,
    val name: String,
    val baseCurrency: String,
    val initialDeposit: BigDecimal,
    val cash: BigDecimal,
    val commissionPerOrder: BigDecimal,
    val slippageBps: Int,
    val status: AccountStatus,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        fun from(account: PaperTradingAccount) = PaperTradingAccountResponse(
            id = account.id,
            name = account.name,
            baseCurrency = account.baseCurrency,
            initialDeposit = account.initialDeposit,
            cash = account.cash,
            commissionPerOrder = account.commissionPerOrder,
            slippageBps = account.slippageBps,
            status = account.status,
            createdAt = account.createdAt,
            updatedAt = account.updatedAt,
        )
    }
}
