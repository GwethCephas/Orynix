package com.cephCoding.orynix.common.exception

import java.util.UUID

class InvalidBacktestRangeException(message: String) : RuntimeException(message)

class InvalidStrategyParametersException(message: String) : RuntimeException(message)

class InvalidAccountCurrencyException(message: String) : RuntimeException(message)

class BacktestNotFoundException(id: UUID) : RuntimeException("Backtest not found: $id")
