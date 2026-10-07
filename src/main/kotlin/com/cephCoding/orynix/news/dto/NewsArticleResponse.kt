package com.cephCoding.orynix.news.dto

import com.cephCoding.orynix.news.domain.NewsArticle
import java.time.Instant
import java.util.UUID

data class NewsArticleResponse(
    val id: UUID?,
    val title: String,
    val description: String?,
    val source: String?,
    val url: String?,
    val author: String?,
    val publishedAt: Instant,
) {
    companion object {
        fun from(article: NewsArticle) = NewsArticleResponse(
            id = article.id,
            title = article.title,
            description = article.description,
            source = article.source,
            url = article.url,
            author = article.author,
            publishedAt = article.publishedAt,
        )
    }
}
