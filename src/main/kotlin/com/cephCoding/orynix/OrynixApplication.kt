package com.cephCoding.orynix

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration
import org.springframework.boot.runApplication

// Excluded deliberately: this API has no UserDetailsService/AuthenticationManager chain
// (see CLAUDE.md section 5) — AuthService checks credentials directly. Without this exclusion,
// Spring Boot still auto-configures an unused in-memory user with a random generated password
// logged on every startup.
@SpringBootApplication(exclude = [UserDetailsServiceAutoConfiguration::class])
@ConfigurationPropertiesScan
class OrynixApplication

fun main(args: Array<String>) {
	runApplication<OrynixApplication>(*args)
}
