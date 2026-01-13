// backend/src/main/kotlin/com/securemonitor/service/AuthService.kt
package com.securemonitor.service

import com.securemonitor.model.Role
import com.securemonitor.model.User
import com.securemonitor.repository.RoleRepository
import com.securemonitor.repository.UserRepository
import com.securemonitor.security.JwtService
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val roleRepository: RoleRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtService: JwtService
) {

    fun register(username: String, email: String, password: String): User {
        if (userRepository.existsByUsername(username)) {
            throw IllegalArgumentException("Username already exists")
        }
        if (userRepository.existsByEmail(email)) {
            throw IllegalArgumentException("Email already exists")
        }

        val userRole = roleRepository.findByName("USER")
            .orElseGet { roleRepository.save(Role(name = "USER", description = "Standard user")) }

        val user = User(
            username = username,
            email = email,
            passwordHash = passwordEncoder.encode(password),
            roles = setOf(userRole)
        )

        return userRepository.save(user)
    }

    fun login(username: String, password: String): String {
        val user = userRepository.findByUsername(username)
            .orElseThrow { IllegalArgumentException("Invalid credentials") }

        if (!passwordEncoder.matches(password, user.passwordHash)) {
            throw IllegalArgumentException("Invalid credentials")
        }

        if (!user.enabled) {
            throw IllegalArgumentException("Account is disabled")
        }

        // Update last login
        userRepository.save(user.copy(lastLogin = Instant.now()))

        val roles = user.roles.map { it.name }
        return jwtService.generateToken(user.username, roles)
    }
}