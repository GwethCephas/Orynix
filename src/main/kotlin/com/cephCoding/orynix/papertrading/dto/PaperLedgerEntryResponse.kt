package com.cephCoding.orynix.papertrading.dto

import com.cephCoding.orynix.papertrading.domain.LedgerEntryType
import com.cephCoding.orynix.papertrading.domain.PaperLedgerEntry
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class PaperLedgerEntryResponse(
    val id: UUID,
    val orderId: UUID?,
    val entryType: LedgerEntryType,
    val cashDelta: BigDecimal,
    val cashBalanceAfter: BigDecimal,
    val description: String,
    val createdAt: Instant,
) {
    companion object {
        fun from(entry: PaperLedgerEntry) = PaperLedgerEntryResponse(
            id = entry.id,
            orderId = entry.orderId,
            entryType = entry.entryType,
            cashDelta = entry.cashDelta,
            cashBalanceAfter = entry.cashBalanceAfter,
            description = entry.description,
            createdAt = entry.createdAt,
        )
    }
}
