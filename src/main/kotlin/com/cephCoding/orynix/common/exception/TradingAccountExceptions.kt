package com.cephCoding.orynix.common.exception

import java.util.UUID

class InvalidTradingAccountProfileException(message: String) : RuntimeException(message)

class TradingAccountProfileNotFoundException(id: UUID) : RuntimeException("Trading account profile not found: $id")
