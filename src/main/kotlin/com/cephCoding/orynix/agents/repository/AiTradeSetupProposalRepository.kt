package com.cephCoding.orynix.agents.repository

import com.cephCoding.orynix.agents.model.AiTradeSetupProposalEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface AiTradeSetupProposalRepository : JpaRepository<AiTradeSetupProposalEntity, UUID> {
    fun findByIdAndUserId(id: UUID, userId: UUID): AiTradeSetupProposalEntity?
}
