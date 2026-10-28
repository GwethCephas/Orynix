package com.cephCoding.orynix.papertrading.service

import com.cephCoding.orynix.common.exception.PaperTradingAccountNotFoundException
import com.cephCoding.orynix.papertrading.domain.AccountStatus
import com.cephCoding.orynix.papertrading.domain.PaperTradingAccount
import com.cephCoding.orynix.papertrading.dto.CreatePaperTradingAccountRequest
import com.cephCoding.orynix.papertrading.dto.UpdatePaperTradingAccountRequest
import com.cephCoding.orynix.papertrading.model.PaperTradingAccountEntity
import com.cephCoding.orynix.papertrading.repository.PaperTradingAccountRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import com.cephCoding.orynix.papertrading.engine.PaperTradingAccounting
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

/**
 * CRUD + ownership for paper trading accounts - mirrors TradingAccountService's shape exactly
 * (findOwned/findOwnedActive, archive-as-soft-delete, identical 404 whether an account is unknown
 * or not owned by the caller). See CLAUDE.md Section 22. Order execution's own account lookup
 * (which additionally takes the pessimistic lock) lives in PaperTradingOrderService, not here -
 * this service never locks, since none of its own operations need to serialize against a
 * concurrent order fill.
 */
@Service
class PaperTradingAccountService(private val repository: PaperTradingAccountRepository) {

    @Transactional
    fun createAccount(userId: UUID, request: CreatePaperTradingAccountRequest): PaperTradingAccount {
        // Explicitly scaled here, not left to a DB round-trip - Hibernate's saveAndFlush returns
        // the same in-memory BigDecimal reference it was given (no implicit reload), so a value
        // entered as "25000" would otherwise come back as scale-0 on THIS response even though a
        // fresh read later would show the NUMERIC(20,4) column's true scale. Matches the
        // BacktestEngine/RiskSizing convention of scaling every monetary output explicitly.
        val initialDeposit = request.initialDeposit.setScale(PaperTradingAccounting.MONEY_SCALE, RoundingMode.HALF_UP)
        val commissionPerOrder = (request.commissionPerOrder ?: BigDecimal.ZERO).setScale(PaperTradingAccounting.MONEY_SCALE, RoundingMode.HALF_UP)
        val entity = PaperTradingAccountEntity(
            userId = userId,
            name = request.name,
            baseCurrency = request.baseCurrency,
            initialDeposit = initialDeposit,
            cash = initialDeposit,
            commissionPerOrder = commissionPerOrder,
            slippageBps = request.slippageBps ?: 0,
        )
        return repository.saveAndFlush(entity).toDomain()
    }

    fun listAccounts(userId: UUID): List<PaperTradingAccount> =
        repository.findAllByUserIdAndStatusOrderByCreatedAtDesc(userId, AccountStatus.ACTIVE.name).map { it.toDomain() }

    // Archived allowed - matches TradingAccountProfile's getProfile precedent (historical viewing).
    fun getAccount(userId: UUID, id: UUID): PaperTradingAccount = findOwned(userId, id).toDomain()

    @Transactional
    fun updateAccount(userId: UUID, id: UUID, request: UpdatePaperTradingAccountRequest): PaperTradingAccount {
        val entity = findOwnedActive(userId, id)
        entity.name = request.name
        return repository.saveAndFlush(entity).toDomain()
    }

    @Transactional
    fun archiveAccount(userId: UUID, id: UUID) {
        val entity = findOwned(userId, id)
        entity.status = AccountStatus.ARCHIVED.name
        repository.saveAndFlush(entity)
    }

    fun findOwned(userId: UUID, id: UUID): PaperTradingAccountEntity =
        repository.findByIdAndUserId(id, userId) ?: throw PaperTradingAccountNotFoundException(id)

    fun findOwnedActive(userId: UUID, id: UUID): PaperTradingAccountEntity {
        val entity = findOwned(userId, id)
        if (entity.status != AccountStatus.ACTIVE.name) {
            throw PaperTradingAccountNotFoundException(id)
        }
        return entity
    }
}
