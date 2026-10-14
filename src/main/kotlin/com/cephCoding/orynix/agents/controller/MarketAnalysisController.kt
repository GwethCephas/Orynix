package com.cephCoding.orynix.agents.controller

import com.cephCoding.orynix.agents.dto.MarketAnalysisRequest
import com.cephCoding.orynix.agents.dto.MarketAnalysisResponse
import com.cephCoding.orynix.agents.service.MarketAnalysisService
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * POST, not GET, matching /api/v1/risk-calculations and /api/v1/backtests: this computes a
 * non-trivial, costly result (one Market Intelligence fetch + one LLM round trip + conditional
 * risk calc) rather than fetching a stored resource, even though - like risk-calculations -
 * nothing is persisted (CLAUDE.md Section 20).
 */
@RestController
@RequestMapping("/api/v1/market-analyses")
class MarketAnalysisController(private val marketAnalysisService: MarketAnalysisService) {

    @PostMapping
    fun analyze(
        @AuthenticationPrincipal userId: UUID,
        @Valid @RequestBody request: MarketAnalysisRequest,
    ): MarketAnalysisResponse = marketAnalysisService.analyze(userId, request)
}
