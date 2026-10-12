package com.cephCoding.orynix.marketintelligence.controller

import com.cephCoding.orynix.marketintelligence.dto.MarketIntelligenceContextResponse
import com.cephCoding.orynix.marketintelligence.service.MarketIntelligenceService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/market-intelligence")
class MarketIntelligenceController(private val marketIntelligenceService: MarketIntelligenceService) {

    @GetMapping
    fun getContext(@RequestParam symbol: String): MarketIntelligenceContextResponse =
        MarketIntelligenceContextResponse.from(marketIntelligenceService.getContext(symbol))
}
