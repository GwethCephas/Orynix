package com.cephCoding.orynix.papertrading.model

import com.cephCoding.orynix.papertrading.domain.LedgerEntryType
import com.cephCoding.orynix.papertrading.domain.PaperLedgerEntry
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EntityListeners
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Immutable, append-only - no updatedAt, same reasoning as PaperOrderEntity. cashBalanceAfter is
 * a denormalized snapshot written once, never recomputed, so the ledger stays independently
 * auditable without re-deriving balances from order rows.
 */
@Entity
@Table(name = "paper_ledger_entries")
@EntityListeners(AuditingEntityListener::class)
class PaperLedgerEntryEntity(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "account_id", nullable = false)
    val accountId: UUID,

    @Column(name = "order_id")
    val orderId: UUID?,

    @Column(name = "entry_type", nullable = false)
    val entryType: String,

    @Column(name = "cash_delta", nullable = false)
    val cashDelta: BigDecimal,

    @Column(name = "cash_balance_after", nullable = false)
    val cashBalanceAfter: BigDecimal,

    @Column(nullable = false)
    val description: String,

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant? = null,
) {
    fun toDomain() = PaperLedgerEntry(
        id = id,
        orderId = orderId,
        entryType = LedgerEntryType.valueOf(entryType),
        cashDelta = cashDelta,
        cashBalanceAfter = cashBalanceAfter,
        description = description,
        createdAt = requireNotNull(createdAt),
    )
}
