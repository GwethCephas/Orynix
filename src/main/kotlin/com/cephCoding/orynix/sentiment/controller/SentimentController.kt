package com.cephCoding.orynix.sentiment.controller

import com.cephCoding.orynix.sentiment.dto.SentimentAggregateResponse
import com.cephCoding.orynix.sentiment.dto.SentimentResponse
import com.cephCoding.orynix.sentiment.service.SentimentService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/news")
class SentimentController(private val sentimentService: SentimentService) {

    @GetMapping("/{newsId}/sentiment")
    fun getSentiment(@PathVariable newsId: UUID): SentimentResponse =
        SentimentResponse.from(sentimentService.getOrAnalyze(newsId))

    @GetMapping("/sentiment")
    fun getAggregate(
        @RequestParam symbol: String,
        @RequestParam(required = false) limit: Int?,
    ): SentimentAggregateResponse = SentimentAggregateResponse.from(sentimentService.getAggregate(symbol, limit))
}
