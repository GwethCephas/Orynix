package com.cephCoding.orynix.papertrading.controller

import com.cephCoding.orynix.papertrading.dto.AccountSummaryResponse
import com.cephCoding.orynix.papertrading.dto.CreatePaperTradingAccountRequest
import com.cephCoding.orynix.papertrading.dto.PaperTradingAccountResponse
import com.cephCoding.orynix.papertrading.dto.UpdatePaperTradingAccountRequest
import com.cephCoding.orynix.papertrading.service.PaperTradingAccountService
import com.cephCoding.orynix.papertrading.service.PaperTradingOrderService
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
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * DELETE archives rather than hard-deletes (PaperTradingAccountService) and returns 204, matching
 * TradingAccountController's identical convention. summary/refresh both delegate to
 * PaperTradingOrderService.getSummary - refresh exists as an explicit action per the API spec but
 * performs the identical computation as the GET (nothing here is cached to invalidate - see
 * CLAUDE.md Section 22).
 */
@RestController
@RequestMapping("/api/v1/paper-trading/accounts")
class PaperTradingAccountController(
    private val accountService: PaperTradingAccountService,
    private val orderService: PaperTradingOrderService,
) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createAccount(
        @AuthenticationPrincipal userId: UUID,
        @Valid @RequestBody request: CreatePaperTradingAccountRequest,
    ): PaperTradingAccountResponse =
        PaperTradingAccountResponse.from(accountService.createAccount(userId, request))

    @GetMapping
    fun listAccounts(@AuthenticationPrincipal userId: UUID): List<PaperTradingAccountResponse> =
        accountService.listAccounts(userId).map(PaperTradingAccountResponse::from)

    @GetMapping("/{accountId}")
    fun getAccount(
        @AuthenticationPrincipal userId: UUID,
        @PathVariable accountId: UUID,
    ): PaperTradingAccountResponse =
        PaperTradingAccountResponse.from(accountService.getAccount(userId, accountId))

    @PutMapping("/{accountId}")
    fun updateAccount(
        @AuthenticationPrincipal userId: UUID,
        @PathVariable accountId: UUID,
        @Valid @RequestBody request: UpdatePaperTradingAccountRequest,
    ): PaperTradingAccountResponse =
        PaperTradingAccountResponse.from(accountService.updateAccount(userId, accountId, request))

    @DeleteMapping("/{accountId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun archiveAccount(
        @AuthenticationPrincipal userId: UUID,
        @PathVariable accountId: UUID,
    ) {
        accountService.archiveAccount(userId, accountId)
    }

    @GetMapping("/{accountId}/summary")
    fun getSummary(
        @AuthenticationPrincipal userId: UUID,
        @PathVariable accountId: UUID,
    ): AccountSummaryResponse =
        AccountSummaryResponse.from(orderService.getSummary(userId, accountId))

    @PostMapping("/{accountId}/refresh")
    fun refresh(
        @AuthenticationPrincipal userId: UUID,
        @PathVariable accountId: UUID,
    ): AccountSummaryResponse =
        AccountSummaryResponse.from(orderService.getSummary(userId, accountId))
}
