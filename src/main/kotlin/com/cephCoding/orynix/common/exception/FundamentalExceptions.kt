package com.cephCoding.orynix.common.exception

class CompanyNotFoundException(symbol: String) : RuntimeException("Company not found: $symbol")

class FundamentalDataProviderException(message: String) : RuntimeException(message)
