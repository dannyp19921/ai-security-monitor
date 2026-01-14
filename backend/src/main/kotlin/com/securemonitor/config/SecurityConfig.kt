// backend/src/main/kotlin/com/securemonitor/config/SecurityConfig.kt
package com.securemonitor.config

import com.securemonitor.security.JwtAuthenticationFilter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

/**
 * Security configuration for the application.
 * 
 * Configures:
 * - CORS settings
 * - CSRF protection (disabled for stateless JWT auth)
 * - Session management (stateless)
 * - Endpoint authorization rules
 * - JWT authentication filter
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
class SecurityConfig(
    private val jwtAuthenticationFilter: JwtAuthenticationFilter
) {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .cors { it.configurationSource(corsConfigurationSource()) }
            .csrf { it.disable() }
            .sessionManagement { 
                // Use stateless sessions for API endpoints
                // OAuth2 authorize endpoint may need session for login flow
                it.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
            }
            .authorizeHttpRequests { auth ->
                auth
                    // Public health check
                    .requestMatchers("/api/health").permitAll()
                    
                    // Authentication endpoints
                    .requestMatchers("/api/auth/**").permitAll()
                    
                    // OAuth 2.0 / OIDC public endpoints
                    .requestMatchers("/.well-known/openid-configuration").permitAll()
                    .requestMatchers("/.well-known/jwks.json").permitAll()
                    .requestMatchers("/oauth2/authorize").permitAll() // Requires auth inside handler
                    .requestMatchers("/oauth2/token").permitAll()
                    
                    // OAuth 2.0 protected endpoints
                    .requestMatchers("/oauth2/userinfo").authenticated()
                    
                    // Admin endpoints
                    .requestMatchers("/api/admin/**").hasRole("ADMIN")
                    
                    // All other requests require authentication
                    .anyRequest().authenticated()
            }
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)

        return http.build()
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration()
        configuration.allowedOrigins = listOf(
            "http://localhost:5173",
            "http://localhost:3000",
            "http://127.0.0.1:5173",
            "http://127.0.0.1:3000",
            "https://ai-security-monitor.vercel.app"
        )
        configuration.allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS")
        configuration.allowedHeaders = listOf("*")
        configuration.exposedHeaders = listOf("Location") // For OAuth2 redirects
        configuration.allowCredentials = true

        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", configuration)
        return source
    }

    @Bean
    fun passwordEncoder(): PasswordEncoder {
        return BCryptPasswordEncoder()
    }
}
