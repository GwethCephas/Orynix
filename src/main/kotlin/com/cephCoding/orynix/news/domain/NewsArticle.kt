package com.cephCoding.orynix.news.domain

import java.time.Instant
import java.util.UUID

data class NewsArticle(
    val id: UUID?,
    val providerArticleId: String,
    val symbol: String,
    val title: String,
    val description: String?,
    val source: String?,
    val url: String?,
    val author: String?,
    val publishedAt: Instant,
)
