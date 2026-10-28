package com.cephCoding.orynix.papertrading.repository

import com.cephCoding.orynix.papertrading.model.PaperPositionEntity
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface PaperPositionRepository : JpaRepository<PaperPositionEntity, UUID> {
    fun findByIdAndAccountId(id: UUID, accountId: UUID): PaperPositionEntity?

    fun findByAccountIdAndSymbolAndStatus(accountId: UUID, symbol: String, status: String): PaperPositionEntity?

    fun findAllByAccountIdAndStatusOrderByOpenedAtDesc(accountId: UUID, status: String): List<PaperPositionEntity>

    fun findAllByAccountIdAndStatusOrderByClosedAtDesc(accountId: UUID, status: String, pageable: Pageable): Page<PaperPositionEntity>
}
