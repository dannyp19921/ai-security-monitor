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
    private val jwtService: JwtService,
    private val auditService: AuditService
) {

    fun register(username: String, email: String, password: String, ipAddress: String? = null): User {
        if (userRepository.existsByUsername(username)) {
            auditService.log(
                action = "REGISTER_FAILED",
                username = username,
                details = "Username already exists",
                ipAddress = ipAddress,
                success = false
            )
            throw IllegalArgumentException("Username already exists")
        }
        if (userRepository.existsByEmail(email)) {
            auditService.log(
                action = "REGISTER_FAILED",
                username = username,
                details = "Email already exists",
                ipAddress = ipAddress,
                success = false
            )
            throw IllegalArgumentException("Email already exists")
        }

        val userRole = roleRepository.findByName("USER")
            .orElseGet { roleRepository.save(Role(name = "USER", description = "Standard user")) }

        val user = User(
            username = username,
            email = email,
            passwordHash = passwordEncoder.encode(password),
            roles = mutableSetOf(userRole)
        )

        val savedUser = userRepository.save(user)

        auditService.log(
            action = "USER_REGISTERED",
            username = username,
            resourceType = "USER",
            resourceId = savedUser.id.toString(),
            ipAddress = ipAddress,
            details = "New user registered with email: $email"
        )

        return savedUser
    }

    fun login(username: String, password: String, ipAddress: String? = null): String {
        val user = userRepository.findByUsername(username).orElse(null)

        if (user == null) {
            auditService.log(
                action = "LOGIN_FAILED",
                username = username,
                details = "User not found",
                ipAddress = ipAddress,
                success = false
            )
            throw IllegalArgumentException("Invalid credentials")
        }

        if (!passwordEncoder.matches(password, user.passwordHash)) {
            auditService.log(
                action = "LOGIN_FAILED",
                username = username,
                details = "Invalid password",
                ipAddress = ipAddress,
                success = false
            )
            throw IllegalArgumentException("Invalid credentials")
        }

        if (!user.enabled) {
            auditService.log(
                action = "LOGIN_FAILED",
                username = username,
                details = "Account disabled",
                ipAddress = ipAddress,
                success = false
            )
            throw IllegalArgumentException("Account is disabled")
        }

        userRepository.save(user.copy(lastLogin = Instant.now()))

        auditService.log(
            action = "USER_LOGIN",
            username = username,
            resourceType = "USER",
            resourceId = user.id.toString(),
            ipAddress = ipAddress
        )

        val roles = user.roles.map { it.name }
        return jwtService.generateToken(user.username, roles)
    }
}