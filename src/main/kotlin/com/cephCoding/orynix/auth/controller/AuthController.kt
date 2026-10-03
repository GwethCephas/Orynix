package com.cephCoding.orynix.auth.controller

import com.cephCoding.orynix.auth.dto.AuthResponse
import com.cephCoding.orynix.auth.dto.LoginRequest
import com.cephCoding.orynix.auth.dto.RefreshTokenRequest
import com.cephCoding.orynix.auth.dto.RegisterRequest
import com.cephCoding.orynix.auth.service.AuthService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(private val authService: AuthService) {

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    fun register(@Valid @RequestBody request: RegisterRequest): AuthResponse = authService.register(request)

    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest): AuthResponse = authService.login(request)

    @PostMapping("/refresh")
    fun refresh(@Valid @RequestBody request: RefreshTokenRequest): AuthResponse = authService.refresh(request)

    @PostMapping("/logout")
    fun logout(@Valid @RequestBody request: RefreshTokenRequest): ResponseEntity<Void> {
        authService.logout(request)
        return ResponseEntity.noContent().build()
    }
}
