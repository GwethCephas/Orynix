package com.cephCoding.orynix.auth.dto

import com.cephCoding.orynix.user.dto.UserResponse

data class AuthResponse(
    val accessToken: String,
    val refreshToken: String,
    val user: UserResponse,
)
