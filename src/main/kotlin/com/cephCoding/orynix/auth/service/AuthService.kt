package com.cephCoding.orynix.auth.service

import com.cephCoding.orynix.auth.dto.AuthResponse
import com.cephCoding.orynix.auth.dto.LoginRequest
import com.cephCoding.orynix.auth.dto.RefreshTokenRequest
import com.cephCoding.orynix.auth.dto.RegisterRequest
import com.cephCoding.orynix.auth.model.RefreshToken
import com.cephCoding.orynix.auth.repository.RefreshTokenRepository
import com.cephCoding.orynix.auth.security.JwtService
import com.cephCoding.orynix.common.exception.EmailAlreadyExistsException
import com.cephCoding.orynix.common.exception.InvalidCredentialsException
import com.cephCoding.orynix.common.exception.InvalidRefreshTokenException
import com.cephCoding.orynix.user.dto.UserResponse
import com.cephCoding.orynix.user.model.User
import com.cephCoding.orynix.user.repository.UserRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtService: JwtService,
    @Value("\${jwt.refresh-token-ttl-seconds}") private val refreshTokenTtlSeconds: Long,
) {
    private val secureRandom = SecureRandom()

    @Transactional
    fun register(request: RegisterRequest): AuthResponse {
        if (userRepository.existsByEmail(request.email)) {
            throw EmailAlreadyExistsException(request.email)
        }
        // PasswordEncoder.encode is JSpecify @Nullable only for a null input; non-null in → non-null out.
        val user = User(email = request.email, passwordHash = passwordEncoder.encode(request.password)!!)
        // User.id is assigned client-side (non-null), so Spring Data's default isNew() check
        // treats this as an update and routes save() through em.merge(), not em.persist() —
        // merge() returns a different managed copy, which is the one AuditingEntityListener
        // actually populates. saveAndFlush's return value (not `user`) must be used below, and
        // the flush is needed so createdAt/updatedAt are set before issueTokens() reads them.
        val savedUser = userRepository.saveAndFlush(user)
        return issueTokens(savedUser)
    }

    @Transactional
    fun login(request: LoginRequest): AuthResponse {
        val user = userRepository.findByEmail(request.email) ?: throw InvalidCredentialsException()
        if (!passwordEncoder.matches(request.password, user.passwordHash)) {
            throw InvalidCredentialsException()
        }
        return issueTokens(user)
    }

    @Transactional
    fun refresh(request: RefreshTokenRequest): AuthResponse {
        val stored = refreshTokenRepository.findByTokenHash(hash(request.refreshToken))
            ?: throw InvalidRefreshTokenException()
        if (!stored.isUsable(Instant.now())) {
            throw InvalidRefreshTokenException()
        }
        stored.revoked = true
        refreshTokenRepository.save(stored)

        val user = userRepository.findById(stored.userId).orElseThrow { InvalidRefreshTokenException() }
        return issueTokens(user)
    }

    @Transactional
    fun logout(request: RefreshTokenRequest) {
        val stored = refreshTokenRepository.findByTokenHash(hash(request.refreshToken)) ?: return
        stored.revoked = true
        refreshTokenRepository.save(stored)
    }

    private fun issueTokens(user: User): AuthResponse {
        val accessToken = jwtService.generateAccessToken(user.id)
        val rawRefreshToken = generateRawRefreshToken()
        refreshTokenRepository.save(
            RefreshToken(
                userId = user.id,
                tokenHash = hash(rawRefreshToken),
                expiresAt = Instant.now().plusSeconds(refreshTokenTtlSeconds),
            )
        )
        return AuthResponse(
            accessToken = accessToken,
            refreshToken = rawRefreshToken,
            user = UserResponse.from(user),
        )
    }

    private fun generateRawRefreshToken(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun hash(raw: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }
}
