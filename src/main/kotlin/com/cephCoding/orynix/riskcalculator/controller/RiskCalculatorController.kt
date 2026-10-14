package com.cephCoding.orynix.riskcalculator.controller

import com.cephCoding.orynix.riskcalculator.dto.RiskCalculationRequest
import com.cephCoding.orynix.riskcalculator.dto.RiskCalculationResponse
import com.cephCoding.orynix.riskcalculator.service.RiskCalculatorService
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/risk-calculations")
class RiskCalculatorController(private val riskCalculatorService: RiskCalculatorService) {

    @PostMapping
    fun calculate(
        @AuthenticationPrincipal userId: UUID,
        @Valid @RequestBody request: RiskCalculationRequest,
    ): RiskCalculationResponse = RiskCalculationResponse.from(riskCalculatorService.calculate(userId, request))
}
