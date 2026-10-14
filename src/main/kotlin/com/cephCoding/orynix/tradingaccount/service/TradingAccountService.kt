package com.cephCoding.orynix.tradingaccount.service

import com.cephCoding.orynix.common.exception.InvalidTradingAccountProfileException
import com.cephCoding.orynix.common.exception.TradingAccountProfileNotFoundException
import com.cephCoding.orynix.tradingaccount.domain.TradingAccountProfile
import com.cephCoding.orynix.tradingaccount.dto.TradingAccountProfileRequest
import com.cephCoding.orynix.tradingaccount.model.TradingAccountProfileEntity
import com.cephCoding.orynix.tradingaccount.repository.TradingAccountProfileRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.util.UUID

/**
 * CRUD + ownership for manually-entered trading account profiles - see CLAUDE.md Section 19.
 * "Delete" is always archive (soft delete): a profile may already be referenced by
 * backtest_runs.account_profile_id rows (see BacktestService's profile integration), and hard
 * deletion would either orphan that provenance or require blocking deletion outright. Mirrors the
 * ownership-scoped-repository-query pattern BacktestService already uses (findByIdAndUserId,
 * identical 404 whether a profile doesn't exist or isn't owned by this user).
 */
@Service
class TradingAccountService(private val repository: TradingAccountProfileRepository) {

    @Transactional
    fun createProfile(userId: UUID, request: TradingAccountProfileRequest): TradingAccountProfile {
        val riskPercent = resolveRiskPercent(request.riskPercentPerTrade)
        validateOptionalPercent(request.maxDailyLossPercent, "maxDailyLossPercent")

        val entity = TradingAccountProfileEntity(
            userId = userId,
            name = request.name,
            platform = request.platform.name,
            platformDetails = request.platformDetails,
            accountCurrency = request.accountCurrency,
            balance = request.balance,
            riskPercentPerTrade = riskPercent,
            maxDailyLossPercent = request.maxDailyLossPercent,
            maxConcurrentPositions = request.maxConcurrentPositions,
        )
        return repository.saveAndFlush(entity).toDomain()
    }

    fun listProfiles(userId: UUID): List<TradingAccountProfile> =
        repository.findAllByUserIdAndArchivedFalseOrderByCreatedAtDesc(userId).map { it.toDomain() }

    fun getProfile(userId: UUID, id: UUID): TradingAccountProfile = findOwned(userId, id).toDomain()

    @Transactional
    fun updateProfile(userId: UUID, id: UUID, request: TradingAccountProfileRequest): TradingAccountProfile {
        val riskPercent = resolveRiskPercent(request.riskPercentPerTrade)
        validateOptionalPercent(request.maxDailyLossPercent, "maxDailyLossPercent")

        // Archived is treated as fully retired here (same as findOwnedProfile below), not just
        // list-hidden: there is no un-archive endpoint, so allowing edits on an archived profile
        // would be a confusing half-open escape hatch with no documented purpose. getProfile
        // deliberately keeps allowing archived (historical viewing), so this is NOT findOwned.
        val entity = findOwnedActive(userId, id)
        entity.name = request.name
        entity.platform = request.platform.name
        entity.platformDetails = request.platformDetails
        entity.accountCurrency = request.accountCurrency
        entity.balance = request.balance
        entity.riskPercentPerTrade = riskPercent
        entity.maxDailyLossPercent = request.maxDailyLossPercent
        entity.maxConcurrentPositions = request.maxConcurrentPositions
        return repository.saveAndFlush(entity).toDomain()
    }

    @Transactional
    fun archiveProfile(userId: UUID, id: UUID) {
        val entity = findOwned(userId, id)
        entity.archived = true
        repository.saveAndFlush(entity)
    }

    /**
     * For BacktestService's and RiskCalculatorService's profile integrations - ownership-checked
     * like findOwned, but ALSO rejects an archived profile, unlike getProfile/updateProfile/
     * archiveProfile which intentionally still allow one (CLAUDE.md Section 19's "archive is a
     * soft delete, not a lockout" reasoning for the GET/PUT endpoints). Archiving is meant to
     * retire a profile going forward, not merely hide it from the list while leaving it fully
     * usable to seed new work via a stale cached id - treating it as 404 here matches the exact
     * visibility a client already has through the non-archived-only list endpoint.
     */
    fun findOwnedProfile(userId: UUID, id: UUID): TradingAccountProfile = findOwnedActive(userId, id).toDomain()

    private fun findOwned(userId: UUID, id: UUID): TradingAccountProfileEntity =
        repository.findByIdAndUserId(id, userId) ?: throw TradingAccountProfileNotFoundException(id)

    private fun findOwnedActive(userId: UUID, id: UUID): TradingAccountProfileEntity {
        val entity = findOwned(userId, id)
        if (entity.archived) {
            throw TradingAccountProfileNotFoundException(id)
        }
        return entity
    }

    private fun resolveRiskPercent(requested: BigDecimal?): BigDecimal {
        val riskPercent = requested ?: DEFAULT_RISK_PERCENT_PER_TRADE
        if (riskPercent.signum() <= 0 || riskPercent > HUNDRED) {
            throw InvalidTradingAccountProfileException("riskPercentPerTrade must be between 0 and 100")
        }
        return riskPercent
    }

    private fun validateOptionalPercent(value: BigDecimal?, fieldName: String) {
        if (value != null && (value.signum() <= 0 || value > HUNDRED)) {
            throw InvalidTradingAccountProfileException("$fieldName must be between 0 and 100")
        }
    }

    companion object {
        private val HUNDRED = BigDecimal(100)
        val DEFAULT_RISK_PERCENT_PER_TRADE: BigDecimal = BigDecimal("1.00")
    }
}
