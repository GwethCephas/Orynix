package com.cephCoding.orynix.common.exception

class InvalidSymbolException(symbol: String) : RuntimeException("Invalid symbol: $symbol")

class InvalidTimeframeException(timeframe: String) : RuntimeException("Invalid timeframe: $timeframe")

class InvalidTimeframeRangeException(message: String) : RuntimeException(message)

class MarketDataProviderException(message: String) : RuntimeException(message)
