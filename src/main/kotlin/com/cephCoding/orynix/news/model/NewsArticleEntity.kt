package com.cephCoding.orynix.news.model

import com.cephCoding.orynix.news.domain.NewsArticle
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EntityListeners
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "news_articles")
@EntityListeners(AuditingEntityListener::class)
class NewsArticleEntity(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "provider_article_id", nullable = false)
    val providerArticleId: String,

    @Column(nullable = false)
    val symbol: String,

    @Column(nullable = false)
    val title: String,

    @Column(nullable = true)
    val description: String?,

    @Column(nullable = true)
    val source: String?,

    @Column(nullable = true)
    val url: String?,

    @Column(nullable = true)
    val author: String?,

    @Column(name = "published_at", nullable = false)
    val publishedAt: Instant,

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant? = null,
) {
    fun toDomain() = NewsArticle(
        id = id,
        providerArticleId = providerArticleId,
        symbol = symbol,
        title = title,
        description = description,
        source = source,
        url = url,
        author = author,
        publishedAt = publishedAt,
    )

    companion object {
        fun from(article: NewsArticle) = NewsArticleEntity(
            providerArticleId = article.providerArticleId,
            symbol = article.symbol,
            title = article.title,
            description = article.description,
            source = article.source,
            url = article.url,
            author = article.author,
            publishedAt = article.publishedAt,
        )
    }
}
