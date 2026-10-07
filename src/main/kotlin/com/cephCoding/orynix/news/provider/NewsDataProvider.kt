package com.cephCoding.orynix.news.provider

import com.cephCoding.orynix.news.domain.NewsArticle

interface NewsDataProvider {
    fun fetchNews(symbol: String, limit: Int): List<NewsArticle>
}
