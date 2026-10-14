package com.cephCoding.orynix.common.exception

import java.util.UUID

class InvalidTradeSetupException(message: String) : RuntimeException(message)

class InvalidTradeSetupTransitionException(message: String) : RuntimeException(message)

class TradeSetupNotFoundException(id: UUID) : RuntimeException("Trade setup not found: $id")
