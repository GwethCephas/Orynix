package com.cephCoding.orynix.signals.controller

import com.cephCoding.orynix.signals.dto.TechnicalSignalSummaryResponse
import com.cephCoding.orynix.signals.service.SignalAnalysisService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/signals")
class SignalAnalysisController(private val signalAnalysisService: SignalAnalysisService) {

    @GetMapping
    fun getSignals(
        @RequestParam symbol: String,
        @RequestParam timeframe: String,
    ): TechnicalSignalSummaryResponse =
        TechnicalSignalSummaryResponse.from(signalAnalysisService.getSignals(symbol, timeframe))
}
