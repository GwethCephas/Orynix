package com.cephCoding.orynix.news.repository

import com.cephCoding.orynix.news.model.NewsArticleEntity
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface NewsArticleRepository : JpaRepository<NewsArticleEntity, UUID> {
    fun findBySymbolOrderByPublishedAtDesc(symbol: String, pageable: Pageable): List<NewsArticleEntity>

    fun findBySymbolAndProviderArticleIdIn(symbol: String, providerArticleIds: Collection<String>): List<NewsArticleEntity>
}
