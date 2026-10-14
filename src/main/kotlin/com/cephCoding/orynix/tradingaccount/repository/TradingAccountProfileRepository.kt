package com.cephCoding.orynix.tradingaccount.repository

import com.cephCoding.orynix.tradingaccount.model.TradingAccountProfileEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface TradingAccountProfileRepository : JpaRepository<TradingAccountProfileEntity, UUID> {
    fun findByIdAndUserId(id: UUID, userId: UUID): TradingAccountProfileEntity?

    fun findAllByUserIdAndArchivedFalseOrderByCreatedAtDesc(userId: UUID): List<TradingAccountProfileEntity>
}
