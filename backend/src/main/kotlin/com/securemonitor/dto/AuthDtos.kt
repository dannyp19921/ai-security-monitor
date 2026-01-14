// backend/src/main/kotlin/com/securemonitor/dto/AuthDtos.kt
package com.securemonitor.dto

data class RegisterRequest(
    val username: String,
    val email: String,
    val password: String
)

data class LoginRequest(
    val username: String,
    val password: String
)

data class AuthResponse(
    val token: String,
    val username: String,
    val roles: List<String>,
    val mfaRequired: Boolean = false
)

data class UserResponse(
    val id: Long,
    val username: String,
    val email: String,
    val roles: List<String>,
    val enabled: Boolean,
    val createdAt: String,
    val lastLogin: String?,
    val mfaEnabled: Boolean = false
)

data class ChatRequest(
    val message: String
)

data class ChatResponse(
    val response: String,
    val timestamp: String = java.time.Instant.now().toString()
)
