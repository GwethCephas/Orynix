package com.cephCoding.orynix.common.exception

class InsufficientCandleDataException(required: Int, actual: Int) :
    RuntimeException("Insufficient candle data: $required candles required, $actual available")
