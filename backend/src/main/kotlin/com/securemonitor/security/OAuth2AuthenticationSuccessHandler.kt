// backend/src/main/kotlin/com/securemonitor/security/OAuth2AuthenticationSuccessHandler.kt
package com.securemonitor.security

import com.securemonitor.model.User
import com.securemonitor.repository.RoleRepository
import com.securemonitor.repository.UserRepository
import com.securemonitor.service.AuditService
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.Authentication
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler
import org.springframework.stereotype.Component
import org.springframework.web.util.UriComponentsBuilder
import java.util.*

@Component
class OAuth2AuthenticationSuccessHandler(
    private val jwtService: JwtService,
    private val userRepository: UserRepository,
    private val roleRepository: RoleRepository,
    private val passwordEncoder: PasswordEncoder,
    private val auditService: AuditService
) : SimpleUrlAuthenticationSuccessHandler() {

    override fun onAuthenticationSuccess(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication
    ) {
        val oauth2User = authentication.principal as OAuth2User
        
        val email = oauth2User.getAttribute<String>("email") 
            ?: throw IllegalStateException("Email not available from OAuth2 provider")
        
        // Find existing user by EMAIL or create new
        val user = userRepository.findByEmail(email).orElseGet {
            createNewUser(email)
        }
        
        val token = jwtService.generateToken(user.username, user.roles.map { it.name })
        
        auditService.log(
            action = "OAUTH2_LOGIN_SUCCESS",
            username = user.username,
            resourceType = "USER",
            resourceId = user.id.toString(),
            ipAddress = request.remoteAddr,
            details = "Logged in via Google OAuth2",
            success = true
        )
        
        val frontendUrl = System.getenv("FRONTEND_URL") ?: "http://localhost:5173"
        val targetUrl = UriComponentsBuilder.fromUriString(frontendUrl)
            .path("/oauth2/callback")
            .queryParam("token", token)
            .build()
            .toUriString()
        
        redirectStrategy.sendRedirect(request, response, targetUrl)
    }
    
    private fun createNewUser(email: String): User {
        logger.info("Creating new user from OAuth2 login: $email")
        
        val userRole = roleRepository.findByName("USER")
            .orElseThrow { IllegalStateException("USER role not found") }
        
        return userRepository.save(User(
            username = email,
            email = email,
            passwordHash = passwordEncoder.encode(UUID.randomUUID().toString()),
            roles = mutableSetOf(userRole)
        ))
    }
}
