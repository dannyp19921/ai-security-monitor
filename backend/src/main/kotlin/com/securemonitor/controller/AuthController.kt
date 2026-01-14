// backend/src/main/kotlin/com/securemonitor/controller/AuthController.kt
package com.securemonitor.controller

import com.securemonitor.dto.AuthResponse
import com.securemonitor.dto.LoginRequest
import com.securemonitor.dto.RegisterRequest
import com.securemonitor.service.AuthService
import com.securemonitor.security.JwtService
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authService: AuthService,
    private val jwtService: JwtService
) {

    @PostMapping("/register")
    fun register(
        @RequestBody request: RegisterRequest,
        httpRequest: HttpServletRequest
    ): ResponseEntity<AuthResponse> {
        val ipAddress = getClientIp(httpRequest)
        val user = authService.register(request.username, request.email, request.password, ipAddress)
        val roles = user.roles.map { it.name }
        val token = jwtService.generateToken(user.username, roles)
        return ResponseEntity.ok(AuthResponse(
            token = token,
            username = user.username,
            roles = roles,
            mfaRequired = false
        ))
    }

    @PostMapping("/login")
    fun login(
        @RequestBody request: LoginRequest,
        httpRequest: HttpServletRequest
    ): ResponseEntity<AuthResponse> {
        val ipAddress = getClientIp(httpRequest)
        val result = authService.login(request.username, request.password, ipAddress)
        
        return ResponseEntity.ok(AuthResponse(
            token = result.token,
            username = result.username,
            roles = result.roles,
            mfaRequired = result.mfaRequired
        ))
    }

    private fun getClientIp(request: HttpServletRequest): String {
        val xForwardedFor = request.getHeader("X-Forwarded-For")
        return if (xForwardedFor != null) {
            xForwardedFor.split(",")[0].trim()
        } else {
            request.remoteAddr
        }
    }
}
