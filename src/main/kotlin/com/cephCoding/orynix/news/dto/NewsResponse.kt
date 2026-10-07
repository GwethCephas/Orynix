package com.cephCoding.orynix.news.dto

import com.cephCoding.orynix.news.domain.NewsResult

data class NewsResponse(
    val symbol: String,
    val articles: List<NewsArticleResponse>,
) {
    companion object {
        fun from(result: NewsResult) = NewsResponse(
            symbol = result.symbol,
            articles = result.articles.map(NewsArticleResponse::from),
        )
    }
}
