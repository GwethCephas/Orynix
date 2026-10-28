package com.cephCoding.orynix.papertrading.repository

import com.cephCoding.orynix.papertrading.model.PaperTradingAccountEntity
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface PaperTradingAccountRepository : JpaRepository<PaperTradingAccountEntity, UUID> {
    fun findByIdAndUserId(id: UUID, userId: UUID): PaperTradingAccountEntity?

    fun findAllByUserIdAndStatusOrderByCreatedAtDesc(userId: UUID, status: String): List<PaperTradingAccountEntity>

    /**
     * The first pessimistic lock in this codebase (CLAUDE.md Section 22) - every order
     * submission and position close acquires this as the first statement inside its
     * transaction, serializing all financial mutations on one account without a second,
     * per-position lock. Deliberately a plain derived-query-shaped @Query (not a derived method
     * name) so @Lock has an unambiguous single query to apply to.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM PaperTradingAccountEntity a WHERE a.id = :id AND a.userId = :userId")
    fun findByIdAndUserIdForUpdate(@Param("id") id: UUID, @Param("userId") userId: UUID): PaperTradingAccountEntity?
}
