package com.cephCoding.orynix.backtesting.controller

import com.cephCoding.orynix.backtesting.dto.BacktestDetailResponse
import com.cephCoding.orynix.backtesting.dto.BacktestRequest
import com.cephCoding.orynix.backtesting.dto.BacktestResponse
import com.cephCoding.orynix.backtesting.dto.BacktestSummaryResponse
import com.cephCoding.orynix.backtesting.service.BacktestService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/backtests")
class BacktestController(private val backtestService: BacktestService) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createBacktest(
        @AuthenticationPrincipal userId: UUID,
        @Valid @RequestBody request: BacktestRequest,
    ): BacktestResponse = BacktestResponse.from(backtestService.createBacktest(userId, request))

    @GetMapping
    fun listBacktests(@AuthenticationPrincipal userId: UUID): List<BacktestSummaryResponse> =
        backtestService.listBacktests(userId).map(BacktestSummaryResponse::from)

    @GetMapping("/{backtestId}")
    fun getBacktest(
        @AuthenticationPrincipal userId: UUID,
        @PathVariable backtestId: UUID,
    ): BacktestResponse = BacktestResponse.from(backtestService.getBacktest(userId, backtestId))

    @GetMapping("/{backtestId}/trades")
    fun getBacktestTrades(
        @AuthenticationPrincipal userId: UUID,
        @PathVariable backtestId: UUID,
    ): BacktestDetailResponse {
        val (trades, equityCurve) = backtestService.getBacktestDetail(userId, backtestId)
        return BacktestDetailResponse.from(trades, equityCurve)
    }
}
