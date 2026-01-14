package com.securemonitor.oauth2.controller

import com.securemonitor.oauth2.dto.*
import com.securemonitor.oauth2.exception.OAuth2Exception
import com.securemonitor.oauth2.service.AuthorizationService
import com.securemonitor.oauth2.service.TokenService
import com.securemonitor.repository.UserRepository
import com.securemonitor.service.AuditService
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpSession
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*
import java.util.Base64

/**
 * OAuth 2.0 / OpenID Connect Controller.
 * 
 * Implements the following endpoints:
 * - GET  /oauth2/authorize     - Authorization endpoint
 * - POST /oauth2/token         - Token endpoint
 * - GET  /oauth2/userinfo      - UserInfo endpoint (OIDC)
 * - GET  /.well-known/openid-configuration - Discovery document
 * - GET  /.well-known/jwks.json - JSON Web Key Set
 * 
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc6749">RFC 6749 - OAuth 2.0</a>
 * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html">OpenID Connect Core</a>
 */
@RestController
class OAuth2Controller(
    private val authorizationService: AuthorizationService,
    private val tokenService: TokenService,
    private val userRepository: UserRepository,
    private val auditService: AuditService,
    @Value("\${oauth2.issuer:http://localhost:8080}")
    private val issuer: String,
    @Value("\${jwt.secret:myDefaultSecretKeyThatIsAtLeast256BitsLong123456}")
    private val jwtSecret: String
) {
    private val log = LoggerFactory.getLogger(OAuth2Controller::class.java)

    /**
     * Authorization endpoint.
     * 
     * This endpoint initiates the OAuth 2.0 authorization flow. It:
     * 1. Validates the authorization request
     * 2. Authenticates the user (if not already authenticated)
     * 3. Obtains user consent
     * 4. Redirects back to the client with an authorization code
     * 
     * In a production system, this would render a consent page. For this
     * demo, we auto-consent if the user is authenticated.
     * 
     * @see <a href="https://datatracker.ietf.org/doc/html/rfc6749#section-4.1.1">RFC 6749 Section 4.1.1</a>
     */
    @GetMapping("/oauth2/authorize")
    fun authorize(
        @RequestParam("response_type") responseType: String,
        @RequestParam("client_id") clientId: String,
        @RequestParam("redirect_uri") redirectUri: String,
        @RequestParam("scope", required = false) scope: String?,
        @RequestParam("state", required = false) state: String?,
        @RequestParam("code_challenge", required = false) codeChallenge: String?,
        @RequestParam("code_challenge_method", required = false, defaultValue = "S256") codeChallengeMethod: String?,
        @RequestParam("nonce", required = false) nonce: String?,
        request: HttpServletRequest
    ): ResponseEntity<Any> {
        log.debug("Authorization request received for client: {}", clientId)
        
        val authRequest = AuthorizationRequest(
            responseType = responseType,
            clientId = clientId,
            redirectUri = redirectUri,
            scope = scope,
            state = state,
            codeChallenge = codeChallenge,
            codeChallengeMethod = codeChallengeMethod,
            nonce = nonce
        )
        
        return try {
            // Validate the request
            val client = authorizationService.validateAuthorizationRequest(authRequest)
            
            // Check if user is authenticated
            val authentication = SecurityContextHolder.getContext().authentication
            if (authentication == null || !authentication.isAuthenticated || 
                authentication.principal == "anonymousUser") {
                // Store request in session and redirect to login
                request.session.setAttribute("oauth2_request", authRequest)
                return ResponseEntity.status(HttpStatus.FOUND)
                    .header("Location", "/login?oauth2=true")
                    .build()
            }
            
            // Get user details
            val username = authentication.name
            val user = userRepository.findByUsername(username).orElseThrow {
                OAuth2Exception.serverError("User not found")
            }
            
            // In production, show consent screen here
            // For demo, we auto-consent
            
            // Create authorization code
            val authCode = authorizationService.createAuthorizationCode(
                client = client,
                request = authRequest,
                userId = user.id,
                username = username
            )
            
            // Build redirect URL
            val redirectUrl = authorizationService.buildAuthorizationRedirect(
                baseRedirectUri = redirectUri,
                code = authCode.code,
                state = state
            )
            
            log.info("Authorization successful, redirecting to: {}", redirectUri)
            ResponseEntity.status(HttpStatus.FOUND)
                .header("Location", redirectUrl)
                .build()
                
        } catch (e: OAuth2Exception) {
            log.warn("Authorization failed: {}", e.errorDescription)
            
            // For security errors that occur before redirect_uri validation,
            // we should not redirect (to prevent open redirect attacks)
            if (e.error == "invalid_client" || e.error == "invalid_request" && 
                e.errorDescription?.contains("redirect_uri") == true) {
                return ResponseEntity.badRequest()
                    .body(e.toErrorResponse())
            }
            
            // For other errors, redirect with error
            val errorRedirect = authorizationService.buildErrorRedirect(
                baseRedirectUri = redirectUri,
                error = e.error,
                errorDescription = e.errorDescription,
                state = state
            )
            ResponseEntity.status(HttpStatus.FOUND)
                .header("Location", errorRedirect)
                .build()
        }
    }

    /**
     * Token endpoint.
     * 
     * Exchanges an authorization code or refresh token for access tokens.
     * 
     * @see <a href="https://datatracker.ietf.org/doc/html/rfc6749#section-4.1.3">RFC 6749 Section 4.1.3</a>
     */
    @PostMapping("/oauth2/token", consumes = [MediaType.APPLICATION_FORM_URLENCODED_VALUE])
    fun token(
        @RequestParam("grant_type") grantType: String,
        @RequestParam("code", required = false) code: String?,
        @RequestParam("redirect_uri", required = false) redirectUri: String?,
        @RequestParam("client_id", required = false) clientId: String?,
        @RequestParam("client_secret", required = false) clientSecret: String?,
        @RequestParam("code_verifier", required = false) codeVerifier: String?,
        @RequestParam("refresh_token", required = false) refreshToken: String?,
        @RequestParam("scope", required = false) scope: String?,
        request: HttpServletRequest
    ): ResponseEntity<Any> {
        log.debug("Token request received. Grant type: {}", grantType)
        
        // Extract client credentials from Authorization header if present
        val (headerClientId, headerClientSecret) = extractBasicAuth(request)
        
        val tokenRequest = TokenRequest(
            grantType = grantType,
            code = code,
            redirectUri = redirectUri,
            clientId = clientId ?: headerClientId,
            clientSecret = clientSecret ?: headerClientSecret,
            codeVerifier = codeVerifier,
            refreshToken = refreshToken,
            scope = scope
        )
        
        return try {
            val response = when (grantType) {
                "authorization_code" -> tokenService.exchangeAuthorizationCode(tokenRequest)
                "refresh_token" -> {
                    // TODO: Implement refresh token flow
                    throw OAuth2Exception.unsupportedGrantType("refresh_token (not yet implemented)")
                }
                else -> throw OAuth2Exception.unsupportedGrantType(grantType)
            }
            
            ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .header("Pragma", "no-cache")
                .body(response)
                
        } catch (e: OAuth2Exception) {
            log.warn("Token request failed: {}", e.errorDescription)
            ResponseEntity.status(e.httpStatus)
                .body(e.toErrorResponse())
        }
    }

    /**
     * Token endpoint with JSON body (alternative).
     */
    @PostMapping("/oauth2/token", consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun tokenJson(@RequestBody tokenRequest: TokenRequest, request: HttpServletRequest): ResponseEntity<Any> {
        return token(
            grantType = tokenRequest.grantType,
            code = tokenRequest.code,
            redirectUri = tokenRequest.redirectUri,
            clientId = tokenRequest.clientId,
            clientSecret = tokenRequest.clientSecret,
            codeVerifier = tokenRequest.codeVerifier,
            refreshToken = tokenRequest.refreshToken,
            scope = tokenRequest.scope,
            request = request
        )
    }

    /**
     * UserInfo endpoint.
     * 
     * Returns claims about the authenticated user.
     * Requires a valid access token with the 'openid' scope.
     * 
     * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html#UserInfo">OIDC UserInfo</a>
     */
    @GetMapping("/oauth2/userinfo")
    fun userInfo(): ResponseEntity<Any> {
        val authentication = SecurityContextHolder.getContext().authentication
        
        if (authentication == null || !authentication.isAuthenticated) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(mapOf("error" to "invalid_token", "error_description" to "Access token is required"))
        }
        
        val username = authentication.name
        val user = userRepository.findByUsername(username).orElse(null)
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(mapOf("error" to "invalid_token", "error_description" to "User not found"))
        
        val userInfo = UserInfoResponse(
            sub = user.id.toString(),
            preferredUsername = user.username,
            email = user.email,
            emailVerified = true, // In production, track this separately
            updatedAt = user.createdAt.epochSecond
        )
        
        return ResponseEntity.ok(userInfo)
    }

    /**
     * OpenID Connect Discovery document.
     * 
     * @see <a href="https://openid.net/specs/openid-connect-discovery-1_0.html">OIDC Discovery</a>
     */
    @GetMapping("/.well-known/openid-configuration")
    fun openIdConfiguration(): ResponseEntity<OpenIdConfiguration> {
        val config = OpenIdConfiguration(
            issuer = issuer,
            authorizationEndpoint = "$issuer/oauth2/authorize",
            tokenEndpoint = "$issuer/oauth2/token",
            userinfoEndpoint = "$issuer/oauth2/userinfo",
            jwksUri = "$issuer/.well-known/jwks.json",
            responseTypesSupported = listOf("code"),
            grantTypesSupported = listOf("authorization_code", "refresh_token"),
            subjectTypesSupported = listOf("public"),
            idTokenSigningAlgValuesSupported = listOf("HS256"), // Using HMAC for demo
            scopesSupported = listOf("openid", "profile", "email"),
            tokenEndpointAuthMethodsSupported = listOf("client_secret_basic", "client_secret_post", "none"),
            codeChallengeMethodsSupported = listOf("S256", "plain"),
            claimsSupported = listOf(
                "sub", "iss", "aud", "exp", "iat", "auth_time",
                "nonce", "preferred_username", "email", "email_verified"
            )
        )
        
        return ResponseEntity.ok(config)
    }

    /**
     * JSON Web Key Set endpoint.
     * 
     * In production, this would expose the public key(s) used to sign tokens.
     * For this demo using HMAC, we return minimal information.
     * 
     * @see <a href="https://datatracker.ietf.org/doc/html/rfc7517">RFC 7517 - JWK</a>
     */
    @GetMapping("/.well-known/jwks.json")
    fun jwks(): ResponseEntity<JwksResponse> {
        // Note: For HMAC signing, we don't expose the key
        // In production with RSA, you would expose the public key here
        val jwks = JwksResponse(
            keys = listOf(
                JwkKey(
                    kty = "oct", // Octet sequence (symmetric)
                    use = "sig",
                    kid = "default-key",
                    alg = "HS256"
                )
            )
        )
        
        return ResponseEntity.ok(jwks)
    }

    /**
     * Extracts client credentials from the Authorization header (Basic auth).
     */
    private fun extractBasicAuth(request: HttpServletRequest): Pair<String?, String?> {
        val authHeader = request.getHeader("Authorization") ?: return Pair(null, null)
        
        if (!authHeader.startsWith("Basic ")) return Pair(null, null)
        
        return try {
            val credentials = String(Base64.getDecoder().decode(authHeader.substring(6)))
            val parts = credentials.split(":", limit = 2)
            if (parts.size == 2) Pair(parts[0], parts[1]) else Pair(null, null)
        } catch (e: Exception) {
            Pair(null, null)
        }
    }

    /**
     * Global exception handler for OAuth2 errors.
     */
    @ExceptionHandler(OAuth2Exception::class)
    fun handleOAuth2Exception(e: OAuth2Exception): ResponseEntity<Map<String, String?>> {
        log.warn("OAuth2 error: {} - {}", e.error, e.errorDescription)
        return ResponseEntity.status(e.httpStatus).body(e.toErrorResponse())
    }
}
