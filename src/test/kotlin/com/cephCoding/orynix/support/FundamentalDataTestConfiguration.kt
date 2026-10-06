package com.cephCoding.orynix.support

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

@TestConfiguration(proxyBeanMethods = false)
class FundamentalDataTestConfiguration {

    @Bean
    @Primary
    fun fakeFundamentalDataProvider(): FakeFundamentalDataProvider = FakeFundamentalDataProvider()
}
