package com.securemonitor.oauth2.model

import jakarta.persistence.*
import java.time.Instant

/**
 * Represents a registered OAuth 2.0 client application.
 * 
 * In OAuth 2.0, a client is an application that requests access to protected
 * resources on behalf of a resource owner (user). Clients must be registered
 * with the authorization server before they can participate in the OAuth flow.
 * 
 * There are two types of clients:
 * - **Confidential clients**: Can securely store credentials (e.g., server-side apps)
 * - **Public clients**: Cannot securely store credentials (e.g., SPAs, mobile apps)
 * 
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc6749#section-2.1">RFC 6749 Section 2.1</a>
 */
@Entity
@Table(name = "oauth2_clients")
data class OAuth2Client(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    
    /**
     * Unique client identifier.
     * This is the public identifier used in OAuth requests.
     */
    @Column(unique = true, nullable = false, length = 100)
    val clientId: String,
    
    /**
     * Client secret (hashed).
     * Only present for confidential clients. Null for public clients.
     */
    @Column(length = 255)
    val clientSecretHash: String? = null,
    
    /**
     * Human-readable name of the client application.
     */
    @Column(nullable = false, length = 255)
    val clientName: String,
    
    /**
     * Description of the client application.
     */
    @Column(length = 1000)
    val description: String? = null,
    
    /**
     * Whether this is a confidential client.
     * Public clients (SPAs, mobile apps) should use PKCE instead of client secret.
     */
    @Column(nullable = false)
    val confidential: Boolean = false,
    
    /**
     * Comma-separated list of allowed redirect URIs.
     * The redirect URI in authorization requests must exactly match one of these.
     */
    @Column(nullable = false, length = 2000)
    val redirectUris: String,
    
    /**
     * Space-separated list of allowed scopes.
     */
    @Column(nullable = false, length = 500)
    val allowedScopes: String = "openid profile email",
    
    /**
     * Space-separated list of allowed grant types.
     */
    @Column(nullable = false, length = 200)
    val allowedGrantTypes: String = "authorization_code refresh_token",
    
    /**
     * Whether PKCE is required for this client.
     * Should be true for public clients.
     */
    @Column(nullable = false)
    val requirePkce: Boolean = true,
    
    /**
     * Access token lifetime in seconds.
     */
    @Column(nullable = false)
    val accessTokenLifetimeSeconds: Long = 3600, // 1 hour
    
    /**
     * Refresh token lifetime in seconds.
     */
    @Column(nullable = false)
    val refreshTokenLifetimeSeconds: Long = 86400 * 30, // 30 days
    
    /**
     * Whether the client is enabled.
     */
    @Column(nullable = false)
    val enabled: Boolean = true,
    
    /**
     * When the client was created.
     */
    @Column(nullable = false)
    val createdAt: Instant = Instant.now(),
    
    /**
     * When the client was last updated.
     */
    @Column
    val updatedAt: Instant? = null
) {
    /**
     * Get redirect URIs as a list.
     */
    fun getRedirectUriList(): List<String> = 
        redirectUris.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    
    /**
     * Check if a redirect URI is allowed for this client.
     */
    fun isRedirectUriAllowed(uri: String): Boolean = 
        getRedirectUriList().contains(uri)
    
    /**
     * Get allowed scopes as a set.
     */
    fun getAllowedScopeSet(): Set<String> =
        allowedScopes.split(" ").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    
    /**
     * Check if a scope is allowed for this client.
     */
    fun isScopeAllowed(scope: String): Boolean =
        getAllowedScopeSet().contains(scope)
    
    /**
     * Check if all requested scopes are allowed.
     */
    fun areAllScopesAllowed(requestedScopes: String?): Boolean {
        if (requestedScopes.isNullOrBlank()) return true
        val requested = requestedScopes.split(" ").map { it.trim() }.filter { it.isNotEmpty() }
        val allowed = getAllowedScopeSet()
        return requested.all { it in allowed }
    }
    
    /**
     * Get allowed grant types as a set.
     */
    fun getAllowedGrantTypeSet(): Set<String> =
        allowedGrantTypes.split(" ").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    
    /**
     * Check if a grant type is allowed for this client.
     */
    fun isGrantTypeAllowed(grantType: String): Boolean =
        getAllowedGrantTypeSet().contains(grantType)
}
