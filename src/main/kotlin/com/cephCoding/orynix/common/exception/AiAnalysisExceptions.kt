package com.cephCoding.orynix.common.exception

class MarketAnalysisProviderException(message: String = "Market analysis provider request failed") : RuntimeException(message)

class MarketAnalysisResponseException(message: String = "Market analysis response was invalid") : RuntimeException(message)
