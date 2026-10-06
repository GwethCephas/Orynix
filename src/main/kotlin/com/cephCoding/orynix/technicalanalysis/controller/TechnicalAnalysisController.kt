package com.cephCoding.orynix.technicalanalysis.controller

import com.cephCoding.orynix.technicalanalysis.dto.TechnicalAnalysisResponse
import com.cephCoding.orynix.technicalanalysis.service.TechnicalAnalysisService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/technical-analysis")
class TechnicalAnalysisController(private val technicalAnalysisService: TechnicalAnalysisService) {

    @GetMapping
    fun getAnalysis(
        @RequestParam symbol: String,
        @RequestParam timeframe: String,
    ): TechnicalAnalysisResponse =
        TechnicalAnalysisResponse.from(technicalAnalysisService.getAnalysis(symbol, timeframe))
}
