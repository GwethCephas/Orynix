package com.cephCoding.orynix.backtesting.repository

import com.cephCoding.orynix.backtesting.model.BacktestEquityPointEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface BacktestEquityPointRepository : JpaRepository<BacktestEquityPointEntity, UUID> {
    fun findByBacktestRunIdOrderBySequenceNumberAsc(backtestRunId: UUID): List<BacktestEquityPointEntity>
}
