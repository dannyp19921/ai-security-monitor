// backend/src/main/kotlin/com/securemonitor/controller/AuthController.kt
package com.securemonitor.controller

import com.securemonitor.dto.AuthResponse
import com.securemonitor.dto.LoginRequest
import com.securemonitor.dto.RegisterRequest
import com.securemonitor.service.AuthService
import com.securemonitor.security.JwtService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authService: AuthService,
    private val jwtService: JwtService
) {

    @PostMapping("/register")
    fun register(@RequestBody request: RegisterRequest): ResponseEntity<AuthResponse> {
        val user = authService.register(request.username, request.email, request.password)
        val roles = user.roles.map { it.name }
        val token = jwtService.generateToken(user.username, roles)

        return ResponseEntity.ok(AuthResponse(
            token = token,
            username = user.username,
            roles = roles
        ))
    }

    @PostMapping("/login")
    fun login(@RequestBody request: LoginRequest): ResponseEntity<AuthResponse> {
        val token = authService.login(request.username, request.password)
        val username = jwtService.getUsername(token)
        val roles = jwtService.getRoles(token)

        return ResponseEntity.ok(AuthResponse(
            token = token,
            username = username,
            roles = roles
        ))
    }
}