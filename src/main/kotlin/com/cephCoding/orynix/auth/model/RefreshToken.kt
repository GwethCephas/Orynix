package com.cephCoding.orynix.auth.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EntityListeners
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "refresh_tokens")
@EntityListeners(AuditingEntityListener::class)
class RefreshToken(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "user_id", nullable = false)
    val userId: UUID,

    @Column(name = "token_hash", nullable = false, unique = true)
    val tokenHash: String,

    @Column(name = "expires_at", nullable = false)
    val expiresAt: Instant,

    @Column(nullable = false)
    var revoked: Boolean = false,

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant? = null,
) {
    fun isUsable(now: Instant): Boolean = !revoked && expiresAt.isAfter(now)
}
