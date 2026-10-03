package com.cephCoding.orynix.common.exception

class EmailAlreadyExistsException(email: String) : RuntimeException("Email already registered: $email")

class InvalidCredentialsException : RuntimeException("Invalid email or password")

class InvalidRefreshTokenException : RuntimeException("Invalid or expired refresh token")
