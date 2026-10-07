package com.cephCoding.orynix.news.provider.massive.dto

data class MassiveNewsResponse(
    val results: List<MassiveNewsArticle>?,
    val status: String?,
)
