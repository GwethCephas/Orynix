package com.cephCoding.orynix.auth.security

import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.UUID
import javax.crypto.SecretKey

@Component
class JwtService(
    @Value("\${jwt.secret}") secret: String,
    @Value("\${jwt.access-token-ttl-seconds}") private val accessTokenTtlSeconds: Long,
) {
    private val key: SecretKey = Keys.hmacShaKeyFor(secret.toByteArray())

    fun generateAccessToken(userId: UUID): String {
        val now = Instant.now()
        return Jwts.builder()
            .subject(userId.toString())
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(accessTokenTtlSeconds, ChronoUnit.SECONDS)))
            .signWith(key)
            .compact()
    }

    fun extractUserId(token: String): UUID? =
        try {
            val subject = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .payload
                .subject
            UUID.fromString(subject)
        } catch (_: JwtException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
}
