package com.cephCoding.orynix.sentiment.repository

import com.cephCoding.orynix.sentiment.model.NewsSentimentEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface NewsSentimentRepository : JpaRepository<NewsSentimentEntity, UUID> {
    fun findByNewsArticleIdAndAnalyzerAndModel(newsArticleId: UUID, analyzer: String, model: String): NewsSentimentEntity?

    fun findByNewsArticleIdInAndAnalyzerAndModel(
        newsArticleIds: Collection<UUID>,
        analyzer: String,
        model: String,
    ): List<NewsSentimentEntity>
}
