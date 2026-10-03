package com.cephCoding.orynix.auth.repository

import com.cephCoding.orynix.auth.model.RefreshToken
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface RefreshTokenRepository : JpaRepository<RefreshToken, UUID> {
    fun findByTokenHash(tokenHash: String): RefreshToken?
}
