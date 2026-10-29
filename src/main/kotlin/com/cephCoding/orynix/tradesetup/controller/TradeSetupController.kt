package com.cephCoding.orynix.tradesetup.controller

import com.cephCoding.orynix.instrument.domain.AssetType
import com.cephCoding.orynix.tradesetup.domain.TradeSetupStatus
import com.cephCoding.orynix.tradesetup.dto.TradeSetupBacktestLinkRequest
import com.cephCoding.orynix.tradesetup.dto.TradeSetupClosureRequest
import com.cephCoding.orynix.tradesetup.dto.TradeSetupCreateRequest
import com.cephCoding.orynix.tradesetup.dto.TradeSetupDecisionRequest
import com.cephCoding.orynix.tradesetup.dto.TradeSetupFromProposalRequest
import com.cephCoding.orynix.tradesetup.dto.TradeSetupPaperOrderRequest
import com.cephCoding.orynix.tradesetup.dto.TradeSetupPaperOrderResponse
import com.cephCoding.orynix.tradesetup.dto.TradeSetupResponse
import com.cephCoding.orynix.tradesetup.dto.TradeSetupRiskEvaluationRequest
import com.cephCoding.orynix.tradesetup.dto.TradeSetupRiskEvaluationResponse
import com.cephCoding.orynix.tradesetup.dto.TradeSetupSummaryResponse
import com.cephCoding.orynix.tradesetup.dto.TradeSetupUpdateRequest
import com.cephCoding.orynix.tradesetup.service.TradeSetupService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * DELETE expires (soft, DRAFT-only) rather than hard-deleting, mirroring
 * TradingAccountController's archive-via-DELETE idiom exactly - 204, no body. All 8 endpoints use
 * only GET/POST/PUT/DELETE, already permitted by SecurityConfig's CORS allowedMethods (added for
 * TradingAccountController) - no SecurityConfig/CORS change needed for this feature.
 */
@RestController
@RequestMapping("/api/v1/trade-setups")
class TradeSetupController(private val tradeSetupService: TradeSetupService) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createTradeSetup(
        @AuthenticationPrincipal userId: UUID,
        @Valid @RequestBody request: TradeSetupCreateRequest,
    ): TradeSetupResponse = TradeSetupResponse.from(tradeSetupService.create(userId, request))

    @GetMapping
    fun listTradeSetups(
        @AuthenticationPrincipal userId: UUID,
        @RequestParam status: TradeSetupStatus?,
        @RequestParam assetType: AssetType?,
        @RequestParam symbol: String?,
    ): List<TradeSetupSummaryResponse> =
        tradeSetupService.list(userId, status, assetType, symbol).map(TradeSetupSummaryResponse::from)

    @GetMapping("/{id}")
    fun getTradeSetup(@AuthenticationPrincipal userId: UUID, @PathVariable id: UUID): TradeSetupResponse =
        TradeSetupResponse.from(tradeSetupService.get(userId, id))

    @PutMapping("/{id}")
    fun updateTradeSetup(
        @AuthenticationPrincipal userId: UUID,
        @PathVariable id: UUID,
        @Valid @RequestBody request: TradeSetupUpdateRequest,
    ): TradeSetupResponse = TradeSetupResponse.from(tradeSetupService.update(userId, id, request))

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun expireTradeSetup(@AuthenticationPrincipal userId: UUID, @PathVariable id: UUID) {
        tradeSetupService.expire(userId, id)
    }

    @PostMapping("/{id}/decision")
    fun decideTradeSetup(
        @AuthenticationPrincipal userId: UUID,
        @PathVariable id: UUID,
        @Valid @RequestBody request: TradeSetupDecisionRequest,
    ): TradeSetupResponse = TradeSetupResponse.from(tradeSetupService.decide(userId, id, request))

    @PostMapping("/{id}/closure")
    fun closeTradeSetup(
        @AuthenticationPrincipal userId: UUID,
        @PathVariable id: UUID,
        @Valid @RequestBody request: TradeSetupClosureRequest,
    ): TradeSetupResponse = TradeSetupResponse.from(tradeSetupService.close(userId, id, request))

    @PostMapping("/{id}/risk-evaluation")
    fun evaluateTradeSetupRisk(
        @AuthenticationPrincipal userId: UUID,
        @PathVariable id: UUID,
        @RequestBody(required = false) request: TradeSetupRiskEvaluationRequest?,
    ): TradeSetupRiskEvaluationResponse =
        TradeSetupRiskEvaluationResponse.from(
            tradeSetupService.evaluateRisk(userId, id, request ?: TradeSetupRiskEvaluationRequest()),
        )

    /** Integration A (CLAUDE.md Section 21): saves a setup from a server-persisted AI proposal. */
    @PostMapping("/from-ai-proposal")
    @ResponseStatus(HttpStatus.CREATED)
    fun createTradeSetupFromProposal(
        @AuthenticationPrincipal userId: UUID,
        @Valid @RequestBody request: TradeSetupFromProposalRequest,
    ): TradeSetupResponse = TradeSetupResponse.from(tradeSetupService.createFromProposal(userId, request))

    /** Integration B (CLAUDE.md Section 21): links an already-run backtest to this setup. */
    @PostMapping("/{id}/backtest-link")
    fun linkTradeSetupBacktest(
        @AuthenticationPrincipal userId: UUID,
        @PathVariable id: UUID,
        @Valid @RequestBody request: TradeSetupBacktestLinkRequest,
    ): TradeSetupResponse = TradeSetupResponse.from(tradeSetupService.linkBacktest(userId, id, request))

    /** Integration C (CLAUDE.md Section 21): "Practice in Paper Trading" from an ACCEPTED setup. */
    @PostMapping("/{id}/paper-order")
    @ResponseStatus(HttpStatus.CREATED)
    fun practiceTradeSetupInPaperTrading(
        @AuthenticationPrincipal userId: UUID,
        @PathVariable id: UUID,
        @Valid @RequestBody request: TradeSetupPaperOrderRequest,
    ): TradeSetupPaperOrderResponse {
        val (tradeSetup, order) = tradeSetupService.practiceInPaperTrading(userId, id, request)
        return TradeSetupPaperOrderResponse.from(tradeSetup, order)
    }
}
