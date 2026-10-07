package com.cephCoding.orynix.support

import com.cephCoding.orynix.news.domain.NewsArticle
import com.cephCoding.orynix.news.provider.NewsDataProvider

class FakeNewsProvider : NewsDataProvider {
    var articlesToReturn: List<NewsArticle> = emptyList()
    var failure: RuntimeException? = null
    var callCount: Int = 0
        private set

    override fun fetchNews(symbol: String, limit: Int): List<NewsArticle> {
        callCount++
        failure?.let { throw it }
        return articlesToReturn
    }

    fun reset() {
        articlesToReturn = emptyList()
        failure = null
        callCount = 0
    }
}
