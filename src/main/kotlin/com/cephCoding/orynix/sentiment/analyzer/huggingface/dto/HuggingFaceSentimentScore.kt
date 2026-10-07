package com.cephCoding.orynix.sentiment.analyzer.huggingface.dto

data class HuggingFaceSentimentScore(
    val label: String,
    val score: Double,
)
