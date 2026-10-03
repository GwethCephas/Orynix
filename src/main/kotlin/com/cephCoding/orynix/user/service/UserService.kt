package com.cephCoding.orynix.user.service

import com.cephCoding.orynix.user.model.User
import com.cephCoding.orynix.user.repository.UserRepository
import org.springframework.stereotype.Service
import java.util.UUID
import java.util.NoSuchElementException

@Service
class UserService(private val userRepository: UserRepository) {
    fun getById(id: UUID): User =
        userRepository.findById(id).orElseThrow { NoSuchElementException("User not found: $id") }
}
