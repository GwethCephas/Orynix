package com.cephCoding.orynix.common.exception

class NewsDataProviderException(message: String) : RuntimeException(message)

class InvalidNewsLimitException(limit: Int) : RuntimeException("Invalid limit: $limit")
