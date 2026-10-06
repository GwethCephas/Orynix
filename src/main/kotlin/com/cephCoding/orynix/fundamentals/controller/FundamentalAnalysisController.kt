package com.cephCoding.orynix.fundamentals.controller

import com.cephCoding.orynix.fundamentals.dto.FundamentalAnalysisResponse
import com.cephCoding.orynix.fundamentals.service.FundamentalAnalysisService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/fundamentals")
class FundamentalAnalysisController(private val fundamentalAnalysisService: FundamentalAnalysisService) {

    @GetMapping
    fun getFundamentals(@RequestParam symbol: String): FundamentalAnalysisResponse =
        FundamentalAnalysisResponse.from(fundamentalAnalysisService.getFundamentals(symbol))
}
