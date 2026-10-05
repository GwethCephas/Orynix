package com.cephCoding.orynix.market.controller

import com.cephCoding.orynix.market.dto.CandleResponse
import com.cephCoding.orynix.market.service.MarketDataService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/market-data")
class MarketDataController(private val marketDataService: MarketDataService) {

    @GetMapping("/candles")
    fun getCandles(
        @RequestParam symbol: String,
        @RequestParam timeframe: String,
    ): List<CandleResponse> =
        marketDataService.getDailyCandles(symbol, timeframe).map(CandleResponse::from)
}
