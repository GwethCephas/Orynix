package com.cephCoding.orynix.backtesting.repository

import com.cephCoding.orynix.backtesting.model.BacktestTradeEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface BacktestTradeRepository : JpaRepository<BacktestTradeEntity, UUID> {
    fun findByBacktestRunIdOrderBySequenceNumberAsc(backtestRunId: UUID): List<BacktestTradeEntity>
}
