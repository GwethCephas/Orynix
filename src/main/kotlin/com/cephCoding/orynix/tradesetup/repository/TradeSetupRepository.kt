package com.cephCoding.orynix.tradesetup.repository

import com.cephCoding.orynix.tradesetup.model.TradeSetupEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface TradeSetupRepository : JpaRepository<TradeSetupEntity, UUID> {
    fun findByIdAndUserId(id: UUID, userId: UUID): TradeSetupEntity?

    /**
     * The first query-param list-filtering endpoint in this codebase (CLAUDE.md Section 21) - no
     * existing precedent to follow, kept to exactly what the feature's spec asks for
     * (status/assetType/symbol), no date-range filtering, no pagination (neither exists anywhere
     * else in this app's REST surface either). @Param is explicit since this project's Kotlin
     * compiler options don't include -java-parameters, so JPQL named-parameter binding can't rely
     * on reflected parameter names.
     */
    @Query(
        """
        SELECT t FROM TradeSetupEntity t
        WHERE t.userId = :userId
          AND (:status IS NULL OR t.status = :status)
          AND (:assetType IS NULL OR t.assetType = :assetType)
          AND (:symbol IS NULL OR t.symbol = :symbol)
        ORDER BY t.createdAt DESC
        """,
    )
    fun findAllByUserIdWithFilters(
        @Param("userId") userId: UUID,
        @Param("status") status: String?,
        @Param("assetType") assetType: String?,
        @Param("symbol") symbol: String?,
    ): List<TradeSetupEntity>
}
