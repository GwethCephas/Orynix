package com.cephCoding.orynix.common.exception

import java.math.BigDecimal
import java.util.UUID

class InvalidPaperOrderException(message: String) : RuntimeException(message)

class InvalidPaperPositionTransitionException(message: String) : RuntimeException(message)

class InsufficientBuyingPowerException(shortfall: BigDecimal, currency: String) :
    RuntimeException("Insufficient buying power: short by $shortfall $currency")

// 422, not 400 - the request itself is well-formed; the latest available price is simply too old
// or absent to honestly fill against. Mirrors InsufficientCandleDataException's "well-formed but
// data-insufficient" convention.
class StalePaperTradingPriceException(symbol: String, ageDays: Long, maxAgeDays: Long) :
    RuntimeException("Latest available price for $symbol is $ageDays day(s) old, exceeding the maximum of $maxAgeDays day(s)")

class PaperTradingPriceUnavailableException(symbol: String) :
    RuntimeException("No price is available for $symbol")

class PaperTradingAccountNotFoundException(id: UUID) : RuntimeException("Paper trading account not found: $id")

class PaperPositionNotFoundException(id: UUID) : RuntimeException("Paper trading position not found: $id")
