package com.cephCoding.orynix.common.exception

import java.util.UUID

class NewsDataProviderException(message: String) : RuntimeException(message)

class InvalidNewsLimitException(limit: Int) : RuntimeException("Invalid limit: $limit")

class NewsArticleNotFoundException(id: UUID) : RuntimeException("News article not found: $id")
