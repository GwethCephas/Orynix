package com.cephCoding.orynix.papertrading.repository

import com.cephCoding.orynix.papertrading.model.PaperOrderEntity
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.math.BigDecimal
import java.util.UUID

interface PaperOrderRepository : JpaRepository<PaperOrderEntity, UUID> {
    fun findByAccountIdAndIdempotencyKey(accountId: UUID, idempotencyKey: String): PaperOrderEntity?

    fun findAllByAccountIdOrderByCreatedAtDesc(accountId: UUID, pageable: Pageable): Page<PaperOrderEntity>

    // Every commission ever charged (opening AND closing fills) - a genuine lifetime total for
    // display, distinct from PaperPosition.totalOpenCommissionPaid, which deliberately counts
    // opening commissions only (the figure the reconciliation identity in CLAUDE.md Section 22
    // actually needs - closing commissions are already netted directly into realizedPnl there).
    @Query("SELECT COALESCE(SUM(o.commission), 0) FROM PaperOrderEntity o WHERE o.accountId = :accountId")
    fun sumCommissionByAccountId(@Param("accountId") accountId: UUID): BigDecimal
}
