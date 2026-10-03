package com.cephCoding.orynix.user.dto

import com.cephCoding.orynix.user.model.User
import java.time.Instant
import java.util.UUID

data class UserResponse(
    val id: UUID,
    val email: String,
    val createdAt: Instant?,
    val updatedAt: Instant?,
) {
    companion object {
        fun from(user: User) = UserResponse(
            id = user.id,
            email = user.email,
            createdAt = user.createdAt,
            updatedAt = user.updatedAt,
        )
    }
}
