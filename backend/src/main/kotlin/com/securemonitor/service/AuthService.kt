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

/**
 * Result of a login attempt
 */
data class LoginResult(
    val token: String,
    val username: String,
    val roles: List<String>,
    val mfaRequired: Boolean
)

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

    /**
     * Authenticate user and return login result.
     * If MFA is enabled, returns mfaRequired=true and a temporary token.
     */
    fun login(username: String, password: String, ipAddress: String? = null): LoginResult {
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

        val roles = user.roles.map { it.name }
        
        // Check if MFA is enabled
        if (user.mfaEnabled) {
            auditService.log(
                action = "LOGIN_MFA_REQUIRED",
                username = username,
                resourceType = "USER",
                resourceId = user.id.toString(),
                ipAddress = ipAddress,
                details = "MFA verification required"
            )
            
            // Generate a temporary token that requires MFA verification
            // This token has a shorter expiry and is marked as pending MFA
            val tempToken = jwtService.generateMfaPendingToken(user.username, roles)
            
            return LoginResult(
                token = tempToken,
                username = user.username,
                roles = roles,
                mfaRequired = true
            )
        }

        // No MFA - complete login
        userRepository.save(user.copy(lastLogin = Instant.now()))

        auditService.log(
            action = "USER_LOGIN",
            username = username,
            resourceType = "USER",
            resourceId = user.id.toString(),
            ipAddress = ipAddress
        )

        val token = jwtService.generateToken(user.username, roles)
        return LoginResult(
            token = token,
            username = user.username,
            roles = roles,
            mfaRequired = false
        )
    }
    
    /**
     * Complete login after MFA verification
     */
    fun completeMfaLogin(username: String, ipAddress: String? = null): String {
        val user = userRepository.findByUsername(username).orElseThrow {
            IllegalArgumentException("User not found")
        }
        
        userRepository.save(user.copy(lastLogin = Instant.now()))
        
        auditService.log(
            action = "USER_LOGIN",
            username = username,
            resourceType = "USER",
            resourceId = user.id.toString(),
            ipAddress = ipAddress,
            details = "Login completed after MFA verification"
        )
        
        val roles = user.roles.map { it.name }
        return jwtService.generateToken(user.username, roles)
    }
}
