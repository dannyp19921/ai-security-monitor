package com.securemonitor.oauth2.service

import com.securemonitor.oauth2.dto.TokenRequest
import com.securemonitor.oauth2.dto.TokenResponse
import com.securemonitor.oauth2.exception.OAuth2Exception
import com.securemonitor.oauth2.model.AuthorizationCode
import com.securemonitor.oauth2.repository.AuthorizationCodeRepository
import com.securemonitor.oauth2.repository.OAuth2ClientRepository
import com.securemonitor.service.AuditService
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.time.Instant
import java.util.*
import javax.crypto.SecretKey

/**
 * Service handling OAuth 2.0 token requests.
 * 
 * This service implements the token endpoint logic as defined in
 * RFC 6749 Section 4.1.3 and Section 6. It handles:
 * - Authorization code exchange
 * - Refresh token exchange
 * - PKCE verification
 * - JWT access token generation
 * - ID token generation (OIDC)
 * 
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc6749#section-4.1.3">RFC 6749 Section 4.1.3</a>
 */
@Service
class TokenService(
    private val clientRepository: OAuth2ClientRepository,
    private val codeRepository: AuthorizationCodeRepository,
    private val pkceService: PkceService,
    private val auditService: AuditService,
    @Value("\${oauth2.issuer:http://localhost:8080}")
    private val issuer: String,
    @Value("\${oauth2.access-token-lifetime:3600}")
    private val accessTokenLifetimeSeconds: Long,
    @Value("\${oauth2.refresh-token-lifetime:86400}")
    private val refreshTokenLifetimeSeconds: Long,
    @Value("\${jwt.secret:myDefaultSecretKeyThatIsAtLeast256BitsLong123456}")
    private val jwtSecret: String = "myDefaultSecretKeyThatIsAtLeast256BitsLong123456"
) {
    private val log = LoggerFactory.getLogger(TokenService::class.java)
    private val secureRandom = SecureRandom()
    
    private val secretKey: SecretKey by lazy {
        Keys.hmacShaKeyFor(jwtSecret.toByteArray())
    }

    companion object {
        /** Supported grant types */
        val SUPPORTED_GRANT_TYPES = setOf(
            "authorization_code",
            "refresh_token"
        )
    }

    /**
     * Exchanges an authorization code for access and refresh tokens.
     * 
     * This method:
     * 1. Validates the authorization code
     * 2. Verifies the client and redirect_uri match
     * 3. Verifies PKCE if present
     * 4. Marks the code as used
     * 5. Generates and returns tokens
     * 
     * @param request The token request
     * @return TokenResponse containing access_token, refresh_token, etc.
     * @throws OAuth2Exception if validation fails
     */
    @Transactional
    fun exchangeAuthorizationCode(request: TokenRequest): TokenResponse {
        log.debug("Processing authorization code exchange for client: {}", request.clientId)
        
        // Validate grant type
        validateGrantType(request.grantType)
        
        // Find and validate authorization code
        val authCode = codeRepository.findValidCode(request.code!!, Instant.now())
            .orElseThrow {
                log.warn("Invalid or expired authorization code")
                OAuth2Exception.invalidGrant("Authorization code is invalid, expired, or has already been used")
            }
        
        // Verify client_id matches
        if (authCode.clientId != request.clientId) {
            log.warn(
                "Client ID mismatch. Code issued to: {}, Request from: {}",
                authCode.clientId, request.clientId
            )
            throw OAuth2Exception.invalidGrant("Authorization code was not issued to this client")
        }
        
        // Verify redirect_uri matches
        if (authCode.redirectUri != request.redirectUri) {
            log.warn(
                "Redirect URI mismatch. Code: {}, Request: {}",
                authCode.redirectUri, request.redirectUri
            )
            throw OAuth2Exception.invalidGrant("redirect_uri does not match the one used in authorization request")
        }
        
        // Get client for additional validation
        val client = clientRepository.findByClientId(request.clientId!!)
            .orElseThrow {
                OAuth2Exception.invalidClient("Client not found")
            }
        
        // Verify PKCE if code challenge was present
        val codeChallenge = authCode.codeChallenge
        if (codeChallenge != null) {
            if (request.codeVerifier.isNullOrBlank()) {
                log.warn("PKCE code_verifier missing for code that requires it")
                throw OAuth2Exception.invalidGrant(
                    "code_verifier is required because code_challenge was used in authorization"
                )
            }
            
            val pkceValid = pkceService.verifyCodeChallenge(
                codeVerifier = request.codeVerifier,
                codeChallenge = codeChallenge,  // <-- Nå fungerer smart cast
                method = authCode.codeChallengeMethod ?: "S256"
            )
            
            if (!pkceValid) {
                log.warn("PKCE verification failed for client: {}", request.clientId)
                auditService.log(
                    action = "OAUTH2_PKCE_FAILED",
                    username = authCode.username,
                    resourceType = "OAUTH2_CLIENT",
                    resourceId = client.clientId,
                    details = "PKCE code_verifier validation failed",
                    success = false
                )
                throw OAuth2Exception.invalidGrant("PKCE verification failed. code_verifier does not match code_challenge")
            }
        }
        
        // Mark code as used (single-use)
        authCode.markAsUsed()
        codeRepository.save(authCode)
        
        // Generate tokens
        val accessToken = generateAccessToken(
            subject = authCode.userId.toString(),
            username = authCode.username,
            clientId = client.clientId,
            scope = authCode.scope,
            lifetime = client.accessTokenLifetimeSeconds
        )
        
        val refreshToken = generateRefreshToken()
        
        // Generate ID token if openid scope was requested
        val idToken = if (authCode.getScopeSet().contains("openid")) {
            generateIdToken(
                subject = authCode.userId.toString(),
                username = authCode.username,
                clientId = client.clientId,
                nonce = authCode.nonce
            )
        } else null
        
        auditService.log(
            action = "OAUTH2_TOKEN_ISSUED",
            username = authCode.username,
            resourceType = "OAUTH2_CLIENT",
            resourceId = client.clientId,
            details = "Tokens issued. Scope: ${authCode.scope ?: "default"}, ID Token: ${idToken != null}",
            success = true
        )
        
        log.info("Tokens issued for user {} to client {}", authCode.username, client.clientId)
        
        return TokenResponse(
            accessToken = accessToken,
            tokenType = "Bearer",
            expiresIn = client.accessTokenLifetimeSeconds,
            refreshToken = refreshToken,
            scope = authCode.scope,
            idToken = idToken
        )
    }

    /**
     * Validates that the grant type is supported.
     * 
     * @param grantType The grant type to validate
     * @throws OAuth2Exception if grant type is not supported
     */
    fun validateGrantType(grantType: String) {
        if (grantType !in SUPPORTED_GRANT_TYPES) {
            throw OAuth2Exception.unsupportedGrantType(grantType)
        }
    }

    /**
     * Generates a JWT access token.
     */
    private fun generateAccessToken(
        subject: String,
        username: String,
        clientId: String,
        scope: String?,
        lifetime: Long
    ): String {
        val now = Instant.now()
        val expiry = now.plusSeconds(lifetime)
        
        return Jwts.builder()
            .subject(subject)
            .issuer(issuer)
            .audience().add(clientId).and()
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiry))
            .claim("username", username)
            .claim("scope", scope ?: "")
            .claim("token_type", "access_token")
            .signWith(secretKey)
            .compact()
    }

    /**
     * Generates an OIDC ID token.
     */
    private fun generateIdToken(
        subject: String,
        username: String,
        clientId: String,
        nonce: String?
    ): String {
        val now = Instant.now()
        val expiry = now.plusSeconds(3600) // ID tokens typically have shorter lifetime
        
        val builder = Jwts.builder()
            .subject(subject)
            .issuer(issuer)
            .audience().add(clientId).and()
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiry))
            .claim("auth_time", now.epochSecond)
            .claim("preferred_username", username)
        
        // Include nonce if provided (for replay protection)
        nonce?.let { builder.claim("nonce", it) }
        
        return builder.signWith(secretKey).compact()
    }

    /**
     * Generates a secure refresh token.
     */
    private fun generateRefreshToken(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
