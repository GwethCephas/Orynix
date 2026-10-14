package com.cephCoding.orynix.tradingaccount.controller

import com.cephCoding.orynix.tradingaccount.dto.TradingAccountProfileRequest
import com.cephCoding.orynix.tradingaccount.dto.TradingAccountProfileResponse
import com.cephCoding.orynix.tradingaccount.service.TradingAccountService
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
 * This feature's DELETE/PUT are the first non-GET/POST endpoints in Orynix - see
 * SecurityConfig.corsConfigurationSource, whose allowedMethods had to be extended to permit them.
 * DELETE archives rather than hard-deletes (TradingAccountService) and returns 204, matching
 * AuthController.logout's existing "no content" convention for a state-changing action with
 * nothing to return.
 */
@RestController
@RequestMapping("/api/v1/trading-accounts")
class TradingAccountController(private val tradingAccountService: TradingAccountService) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createProfile(
        @AuthenticationPrincipal userId: UUID,
        @Valid @RequestBody request: TradingAccountProfileRequest,
    ): TradingAccountProfileResponse =
        TradingAccountProfileResponse.from(tradingAccountService.createProfile(userId, request))

    @GetMapping
    fun listProfiles(@AuthenticationPrincipal userId: UUID): List<TradingAccountProfileResponse> =
        tradingAccountService.listProfiles(userId).map(TradingAccountProfileResponse::from)

    @GetMapping("/{profileId}")
    fun getProfile(
        @AuthenticationPrincipal userId: UUID,
        @PathVariable profileId: UUID,
    ): TradingAccountProfileResponse =
        TradingAccountProfileResponse.from(tradingAccountService.getProfile(userId, profileId))

    @PutMapping("/{profileId}")
    fun updateProfile(
        @AuthenticationPrincipal userId: UUID,
        @PathVariable profileId: UUID,
        @Valid @RequestBody request: TradingAccountProfileRequest,
    ): TradingAccountProfileResponse =
        TradingAccountProfileResponse.from(tradingAccountService.updateProfile(userId, profileId, request))

    @DeleteMapping("/{profileId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun archiveProfile(
        @AuthenticationPrincipal userId: UUID,
        @PathVariable profileId: UUID,
    ) {
        tradingAccountService.archiveProfile(userId, profileId)
    }
}
