package com.cephCoding.orynix.news.controller

import com.cephCoding.orynix.news.dto.NewsResponse
import com.cephCoding.orynix.news.service.NewsService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/news")
class NewsController(private val newsService: NewsService) {

    @GetMapping
    fun getNews(
        @RequestParam symbol: String,
        @RequestParam(required = false) limit: Int?,
    ): NewsResponse = NewsResponse.from(newsService.getNews(symbol, limit))
}
