package com.securemonitor.oauth2.model

import jakarta.persistence.*
import java.time.Instant

/**
 * Represents an OAuth 2.0 authorization code.
 * 
 * Authorization codes are short-lived, single-use tokens that are exchanged
 * for access tokens. They are a critical part of the authorization code flow
 * and must be handled securely.
 * 
 * Security considerations:
 * - Codes are single-use and should be invalidated after exchange
 * - Codes have a short lifetime (typically 10 minutes)
 * - Codes are bound to a specific client and redirect URI
 * - PKCE parameters are stored to validate the token request
 * 
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc6749#section-4.1.2">RFC 6749 Section 4.1.2</a>
 */
@Entity
@Table(
    name = "oauth2_authorization_codes",
    indexes = [
        Index(name = "idx_auth_code_code", columnList = "code"),
        Index(name = "idx_auth_code_client_id", columnList = "clientId"),
        Index(name = "idx_auth_code_expires_at", columnList = "expiresAt")
    ]
)
data class AuthorizationCode(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    
    /**
     * The authorization code value.
     * This is a cryptographically random string.
     */
    @Column(unique = true, nullable = false, length = 100)
    val code: String,
    
    /**
     * The client ID that requested this authorization.
     */
    @Column(nullable = false, length = 100)
    val clientId: String,
    
    /**
     * The ID of the user who authorized the request.
     */
    @Column(nullable = false)
    val userId: Long,
    
    /**
     * The username of the user who authorized the request.
     * Stored for audit purposes.
     */
    @Column(nullable = false, length = 100)
    val username: String,
    
    /**
     * The redirect URI used in the authorization request.
     * Must be validated when exchanging the code for tokens.
     */
    @Column(nullable = false, length = 2000)
    val redirectUri: String,
    
    /**
     * Space-separated list of granted scopes.
     */
    @Column(length = 500)
    val scope: String? = null,
    
    /**
     * PKCE code challenge.
     * Required if PKCE was used in the authorization request.
     */
    @Column(length = 128)
    val codeChallenge: String? = null,
    
    /**
     * PKCE code challenge method (S256 or plain).
     */
    @Column(length = 10)
    val codeChallengeMethod: String? = null,
    
    /**
     * Nonce value for ID token (OIDC).
     * If present, must be included in the ID token.
     */
    @Column(length = 255)
    val nonce: String? = null,
    
    /**
     * When the authorization code was issued.
     */
    @Column(nullable = false)
    val issuedAt: Instant = Instant.now(),
    
    /**
     * When the authorization code expires.
     * Default is 10 minutes after issuance.
     */
    @Column(nullable = false)
    val expiresAt: Instant = Instant.now().plusSeconds(600),
    
    /**
     * Whether this code has been used.
     * Authorization codes are single-use.
     */
    @Column(nullable = false)
    var used: Boolean = false,
    
    /**
     * When the code was used (for audit purposes).
     */
    @Column
    var usedAt: Instant? = null
) {
    /**
     * Check if this authorization code has expired.
     */
    fun isExpired(): Boolean = Instant.now().isAfter(expiresAt)
    
    /**
     * Check if this authorization code is still valid.
     * A code is valid if it has not expired and has not been used.
     */
    fun isValid(): Boolean = !isExpired() && !used
    
    /**
     * Mark this authorization code as used.
     * This should be called atomically during token exchange.
     */
    fun markAsUsed() {
        used = true
        usedAt = Instant.now()
    }
    
    /**
     * Check if PKCE validation is required.
     */
    fun requiresPkce(): Boolean = codeChallenge != null
    
    /**
     * Get granted scopes as a set.
     */
    fun getScopeSet(): Set<String> =
        scope?.split(" ")?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()
    
    companion object {
        /** Default lifetime for authorization codes in seconds */
        const val DEFAULT_LIFETIME_SECONDS = 600L // 10 minutes
        
        /** Length of the random authorization code */
        const val CODE_LENGTH = 32
    }
}
