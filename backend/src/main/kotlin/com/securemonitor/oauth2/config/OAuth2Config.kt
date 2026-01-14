package com.securemonitor.oauth2.config

import com.securemonitor.oauth2.model.OAuth2Client
import com.securemonitor.oauth2.repository.OAuth2ClientRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Configuration properties for OAuth 2.0 / OIDC.
 */
@Configuration
@ConfigurationProperties(prefix = "oauth2")
class OAuth2Config {
    
    /** The issuer identifier (typically the base URL of the authorization server) */
    var issuer: String = "http://localhost:8080"
    
    /** Access token lifetime in seconds */
    var accessTokenLifetime: Long = 3600 // 1 hour
    
    /** Refresh token lifetime in seconds */
    var refreshTokenLifetime: Long = 86400 * 30 // 30 days
    
    /** Authorization code lifetime in seconds */
    var authorizationCodeLifetime: Long = 600 // 10 minutes
    
    /** Whether to require PKCE for all public clients */
    var requirePkce: Boolean = true
}

/**
 * Initializes default OAuth2 clients for development/testing.
 */
@Configuration
class OAuth2ClientInitializer {
    
    private val log = LoggerFactory.getLogger(OAuth2ClientInitializer::class.java)
    
    /**
     * Creates default OAuth2 clients for development.
     * In production, clients would be registered through an admin API.
     */
    @Bean
    fun initializeOAuth2Clients(clientRepository: OAuth2ClientRepository) = CommandLineRunner {
        // Create a demo client for the frontend
        if (!clientRepository.existsByClientId("ai-security-monitor-frontend")) {
            val frontendClient = OAuth2Client(
                clientId = "ai-security-monitor-frontend",
                clientSecretHash = null, // Public client, uses PKCE
                clientName = "AI Security Monitor Frontend",
                description = "The React frontend application",
                confidential = false,
                redirectUris = listOf(
                    "http://localhost:5173/callback",
                    "http://localhost:5173/oauth/callback",
                    "http://localhost:3000/callback",
                    "https://ai-security-monitor.vercel.app/callback",
                    "https://ai-security-monitor.vercel.app/oauth/callback"
                ).joinToString(","),
                allowedScopes = "openid profile email",
                allowedGrantTypes = "authorization_code refresh_token",
                requirePkce = true,
                accessTokenLifetimeSeconds = 3600,
                refreshTokenLifetimeSeconds = 86400 * 7
            )
            
            clientRepository.save(frontendClient)
            log.info("Created default OAuth2 client: ai-security-monitor-frontend")
        }
        
        // Create a demo client for testing
        if (!clientRepository.existsByClientId("test-client")) {
            val testClient = OAuth2Client(
                clientId = "test-client",
                clientSecretHash = null,
                clientName = "Test Client",
                description = "A test client for development",
                confidential = false,
                redirectUris = "http://localhost:3000/callback,http://127.0.0.1:3000/callback",
                allowedScopes = "openid profile email",
                allowedGrantTypes = "authorization_code refresh_token",
                requirePkce = true,
                accessTokenLifetimeSeconds = 3600,
                refreshTokenLifetimeSeconds = 86400
            )
            
            clientRepository.save(testClient)
            log.info("Created default OAuth2 client: test-client")
        }
        
        // Create a confidential client example (for server-to-server)
        if (!clientRepository.existsByClientId("confidential-client")) {
            val confidentialClient = OAuth2Client(
                clientId = "confidential-client",
                // In production, this would be a properly hashed secret
                clientSecretHash = "\$2a\$10\$dummyHashForDemoOnly",
                clientName = "Confidential Backend Client",
                description = "A confidential client for backend services",
                confidential = true,
                redirectUris = "http://localhost:8081/callback",
                allowedScopes = "openid profile email admin",
                allowedGrantTypes = "authorization_code refresh_token client_credentials",
                requirePkce = false, // Confidential clients can use client_secret instead
                accessTokenLifetimeSeconds = 3600,
                refreshTokenLifetimeSeconds = 86400 * 30
            )
            
            clientRepository.save(confidentialClient)
            log.info("Created default OAuth2 client: confidential-client")
        }
        
        log.info("OAuth2 client initialization complete")
    }
}
