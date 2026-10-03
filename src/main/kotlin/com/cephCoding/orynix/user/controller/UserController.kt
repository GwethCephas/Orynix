package com.cephCoding.orynix.user.controller

import com.cephCoding.orynix.user.dto.UserResponse
import com.cephCoding.orynix.user.service.UserService
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/users")
class UserController(private val userService: UserService) {

    @GetMapping("/me")
    fun me(@AuthenticationPrincipal userId: UUID): UserResponse =
        UserResponse.from(userService.getById(userId))
}
