package com.cephCoding.orynix.sentiment.analyzer.huggingface

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "huggingface")
data class HuggingFaceProperties(
    val baseUrl: String,
    val apiKey: String,
    val model: String,
)
