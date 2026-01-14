package com.securemonitor.oauth2.service

import com.securemonitor.oauth2.dto.AuthorizationRequest
import com.securemonitor.oauth2.exception.OAuth2Exception
import com.securemonitor.oauth2.model.AuthorizationCode
import com.securemonitor.oauth2.model.OAuth2Client
import com.securemonitor.oauth2.repository.AuthorizationCodeRepository
import com.securemonitor.oauth2.repository.OAuth2ClientRepository
import com.securemonitor.service.AuditService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64

/**
 * Service handling OAuth 2.0 authorization requests.
 * 
 * This service implements the authorization endpoint logic as defined in
 * RFC 6749 Section 4.1. It handles:
 * - Request validation
 * - Client authentication
 * - PKCE validation
 * - Authorization code generation
 * - Redirect URI construction
 * 
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc6749#section-4.1">RFC 6749 Section 4.1</a>
 */
@Service
class AuthorizationService(
    private val clientRepository: OAuth2ClientRepository,
    private val codeRepository: AuthorizationCodeRepository,
    private val pkceService: PkceService,
    private val auditService: AuditService
) {
    private val log = LoggerFactory.getLogger(AuthorizationService::class.java)
    private val secureRandom = SecureRandom()

    companion object {
        /** Supported response types */
        val SUPPORTED_RESPONSE_TYPES = setOf("code")
        
        /** Length of generated authorization codes */
        const val CODE_LENGTH = 32
        
        /** Authorization code lifetime in seconds */
        const val CODE_LIFETIME_SECONDS = 600L // 10 minutes
    }

    /**
     * Validates an authorization request and returns the validated client.
     * 
     * This method performs all validation required before showing the consent
     * screen to the user. Validation includes:
     * - response_type must be "code"
     * - client_id must exist and be enabled
     * - redirect_uri must be registered for the client
     * - scope must be allowed for the client
     * - PKCE must be present if required by the client
     * 
     * @param request The authorization request to validate
     * @return The validated OAuth2Client
     * @throws OAuth2Exception if validation fails
     */
    fun validateAuthorizationRequest(request: AuthorizationRequest): OAuth2Client {
        log.debug("Validating authorization request for client: {}", request.clientId)
        
        // Validate response_type
        if (request.responseType !in SUPPORTED_RESPONSE_TYPES) {
            log.warn("Unsupported response_type: {}", request.responseType)
            throw OAuth2Exception(
                error = "unsupported_response_type",
                errorDescription = "Response type '${request.responseType}' is not supported. " +
                    "Supported types: $SUPPORTED_RESPONSE_TYPES"
            )
        }
        
        // Find and validate client
        val client = clientRepository.findByClientId(request.clientId).orElseThrow {
            log.warn("Unknown client_id: {}", request.clientId)
            OAuth2Exception.invalidClient("Client '${request.clientId}' not found")
        }
        
        // Check if client is enabled
        if (!client.enabled) {
            log.warn("Disabled client attempted authorization: {}", request.clientId)
            throw OAuth2Exception.invalidClient("Client '${request.clientId}' is disabled")
        }
        
        // Validate redirect_uri
        if (!client.isRedirectUriAllowed(request.redirectUri)) {
            log.warn(
                "Invalid redirect_uri for client {}: {}. Allowed: {}",
                request.clientId, request.redirectUri, client.redirectUris
            )
            throw OAuth2Exception.invalidRequest(
                "Invalid redirect_uri. The redirect URI must exactly match a registered URI."
            )
        }
        
        // Validate scope
        if (!client.areAllScopesAllowed(request.scope)) {
            log.warn(
                "Invalid scope for client {}: {}. Allowed: {}",
                request.clientId, request.scope, client.allowedScopes
            )
            throw OAuth2Exception.invalidScope(
                "One or more requested scopes are not allowed for this client. " +
                "Allowed scopes: ${client.allowedScopes}"
            )
        }
        
        // Validate PKCE if required
        if (client.requirePkce && request.codeChallenge.isNullOrBlank()) {
            log.warn("PKCE required but not provided for client: {}", request.clientId)
            throw OAuth2Exception.invalidRequest(
                "PKCE is required for this client. " +
                "Please include code_challenge and code_challenge_method parameters."
            )
        }
        
        // Validate code_challenge_method if code_challenge is present
        if (!request.codeChallenge.isNullOrBlank()) {
            val method = request.codeChallengeMethod ?: "plain"
            if (method !in PkceService.SUPPORTED_METHODS) {
                throw OAuth2Exception.invalidRequest(
                    "Unsupported code_challenge_method: $method. " +
                    "Supported methods: ${PkceService.SUPPORTED_METHODS}"
                )
            }
        }
        
        log.debug("Authorization request validated successfully for client: {}", request.clientId)
        return client
    }

    /**
     * Creates an authorization code after user consent.
     * 
     * The authorization code is a cryptographically secure random string that
     * is bound to the client, user, redirect URI, and PKCE parameters. It is
     * single-use and short-lived.
     * 
     * @param client The validated OAuth2 client
     * @param request The original authorization request
     * @param userId The ID of the user who consented
     * @param username The username of the user who consented
     * @return The created AuthorizationCode
     */
    @Transactional
    fun createAuthorizationCode(
        client: OAuth2Client,
        request: AuthorizationRequest,
        userId: Long,
        username: String
    ): AuthorizationCode {
        val codeValue = generateSecureCode()
        val now = Instant.now()
        
        val authorizationCode = AuthorizationCode(
            code = codeValue,
            clientId = client.clientId,
            userId = userId,
            username = username,
            redirectUri = request.redirectUri,
            scope = request.scope,
            codeChallenge = request.codeChallenge,
            codeChallengeMethod = request.codeChallengeMethod,
            nonce = request.nonce,
            issuedAt = now,
            expiresAt = now.plusSeconds(CODE_LIFETIME_SECONDS)
        )
        
        val savedCode = codeRepository.save(authorizationCode)
        
        auditService.log(
            action = "OAUTH2_CODE_ISSUED",
            username = username,
            resourceType = "OAUTH2_CLIENT",
            resourceId = client.clientId,
            details = "Authorization code issued. Scope: ${request.scope ?: "default"}, " +
                "PKCE: ${request.codeChallenge != null}",
            success = true
        )
        
        log.info(
            "Authorization code issued for user {} to client {}",
            username, client.clientId
        )
        
        return savedCode
    }

    /**
     * Builds the redirect URI with the authorization code.
     * 
     * @param baseRedirectUri The base redirect URI from the request
     * @param code The authorization code
     * @param state The state parameter (if provided)
     * @return The complete redirect URI
     */
    fun buildAuthorizationRedirect(
        baseRedirectUri: String,
        code: String,
        state: String?
    ): String {
        val params = mutableListOf<String>()
        params.add("code=${urlEncode(code)}")
        state?.let { params.add("state=${urlEncode(it)}") }
        
        return appendQueryParams(baseRedirectUri, params)
    }

    /**
     * Builds an error redirect URI.
     * 
     * @param baseRedirectUri The base redirect URI
     * @param error The error code
     * @param errorDescription Human-readable error description
     * @param state The state parameter (if provided)
     * @return The error redirect URI
     */
    fun buildErrorRedirect(
        baseRedirectUri: String,
        error: String,
        errorDescription: String?,
        state: String?
    ): String {
        val params = mutableListOf<String>()
        params.add("error=${urlEncode(error)}")
        errorDescription?.let { params.add("error_description=${urlEncode(it)}") }
        state?.let { params.add("state=${urlEncode(it)}") }
        
        return appendQueryParams(baseRedirectUri, params)
    }

    /**
     * Generates a cryptographically secure authorization code.
     */
    private fun generateSecureCode(): String {
        val bytes = ByteArray(CODE_LENGTH)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    /**
     * URL-encodes a string.
     */
    private fun urlEncode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8)

    /**
     * Appends query parameters to a URI.
     */
    private fun appendQueryParams(uri: String, params: List<String>): String {
        if (params.isEmpty()) return uri
        
        val separator = if (uri.contains("?")) "&" else "?"
        return uri + separator + params.joinToString("&")
    }
}
