package com.cephCoding.orynix.market.controller

import com.cephCoding.orynix.market.dto.CandleResponse
import com.cephCoding.orynix.market.service.MarketDataService
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@RestController
@RequestMapping("/api/v1/market-data")
class MarketDataController(private val marketDataService: MarketDataService) {

    /**
     * `from`/`to` are optional and additive — omitting both preserves the original, unchanged
     * behavior (the hardcoded trailing-365-day daily window via [MarketDataService.getDailyCandles]).
     * Supplying both routes to [MarketDataService.getCandlesInRange] instead, the only path that
     * accepts intraday timeframes (CLAUDE.md Section 6's timeframe extension) — a request
     * supplying only one of the two is treated the same as supplying neither, since a half-open
     * range has no well-defined meaning here.
     */
    @GetMapping("/candles")
    fun getCandles(
        @RequestParam symbol: String,
        @RequestParam timeframe: String,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate?,
    ): List<CandleResponse> =
        if (from != null && to != null) {
            marketDataService.getCandlesInRange(symbol, timeframe, from, to).map(CandleResponse::from)
        } else {
            marketDataService.getDailyCandles(symbol, timeframe).map(CandleResponse::from)
        }
}
