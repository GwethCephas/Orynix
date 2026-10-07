package com.cephCoding.orynix.news.provider.massive.dto

import com.fasterxml.jackson.annotation.JsonProperty

data class MassiveNewsArticle(
    val id: String?,
    val title: String?,
    val author: String?,
    val description: String?,
    @JsonProperty("article_url") val articleUrl: String?,
    @JsonProperty("published_utc") val publishedUtc: String?,
    val publisher: MassiveNewsPublisher?,
)
