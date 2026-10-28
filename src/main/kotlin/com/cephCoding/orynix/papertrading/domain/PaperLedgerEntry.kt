package com.cephCoding.orynix.papertrading.domain

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * One immutable row of the account's auditable cash history. `cashBalanceAfter` is a
 * denormalized snapshot taken at write time - the ledger is independently auditable without
 * re-deriving balances from order rows.
 */
data class PaperLedgerEntry(
    val id: UUID,
    val orderId: UUID?,
    val entryType: LedgerEntryType,
    val cashDelta: BigDecimal,
    val cashBalanceAfter: BigDecimal,
    val description: String,
    val createdAt: Instant,
)
