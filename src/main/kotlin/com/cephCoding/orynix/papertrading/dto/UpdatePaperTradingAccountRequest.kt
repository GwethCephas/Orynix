package com.cephCoding.orynix.papertrading.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

// Rename is the only mutable account setting - baseCurrency/initialDeposit are fixed at creation,
// matching how backtest_runs freezes a run's own account_currency/initial_capital permanently.
data class UpdatePaperTradingAccountRequest(
    @field:NotBlank
    @field:Size(max = 100)
    val name: String,
)
