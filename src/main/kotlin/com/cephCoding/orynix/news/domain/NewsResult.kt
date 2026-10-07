package com.cephCoding.orynix.news.domain

data class NewsResult(
    val symbol: String,
    val articles: List<NewsArticle>,
)
