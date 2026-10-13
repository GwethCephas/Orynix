package com.cephCoding.orynix.backtesting.repository

import com.cephCoding.orynix.backtesting.model.BacktestRunEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface BacktestRunRepository : JpaRepository<BacktestRunEntity, UUID> {
    fun findByIdAndUserId(id: UUID, userId: UUID): BacktestRunEntity?

    fun findAllByUserIdOrderByCompletedAtDesc(userId: UUID): List<BacktestRunEntity>
}
