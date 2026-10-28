package com.cephCoding.orynix.papertrading.repository

import com.cephCoding.orynix.papertrading.model.PaperLedgerEntryEntity
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface PaperLedgerEntryRepository : JpaRepository<PaperLedgerEntryEntity, UUID> {
    fun findAllByAccountIdOrderByCreatedAtDesc(accountId: UUID, pageable: Pageable): Page<PaperLedgerEntryEntity>
}
