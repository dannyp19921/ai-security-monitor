package com.securemonitor.oauth2.dto

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant

/**
 * Request parameters for the authorization endpoint (/oauth2/authorize).
 * 
 * This follows RFC 6749 Section 4.1.1 and RFC 7636 for PKCE parameters.
 * 
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc6749#section-4.1.1">RFC 6749 Section 4.1.1</a>
 */
data class AuthorizationRequest(
    /** The type of response expected. Must be "code" for authorization code flow. */
    @JsonProperty("response_type")
    val responseType: String,
    
    /** The client identifier registered with the authorization server. */
    @JsonProperty("client_id")
    val clientId: String,
    
    /** The URI to redirect the user-agent after authorization. */
    @JsonProperty("redirect_uri")
    val redirectUri: String,
    
    /** Space-delimited list of requested scopes. */
    val scope: String? = null,
    
    /** Opaque value to maintain state between request and callback. */
    val state: String? = null,
    
    /** PKCE code challenge (required for public clients). */
    @JsonProperty("code_challenge")
    val codeChallenge: String? = null,
    
    /** PKCE code challenge method (S256 recommended). */
    @JsonProperty("code_challenge_method")
    val codeChallengeMethod: String? = "S256",
    
    /** Optional nonce for ID token replay protection (OIDC). */
    val nonce: String? = null
) {
    companion object {
        const val RESPONSE_TYPE_CODE = "code"
        const val RESPONSE_TYPE_TOKEN = "token" // Implicit flow (not recommended)
    }
}

/**
 * Request body for the token endpoint (/oauth2/token).
 * 
 * Supports multiple grant types as defined in RFC 6749.
 * 
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc6749#section-4.1.3">RFC 6749 Section 4.1.3</a>
 */
data class TokenRequest(
    /** The grant type being used. */
    @JsonProperty("grant_type")
    val grantType: String,
    
    /** Authorization code (for authorization_code grant). */
    val code: String? = null,
    
    /** Redirect URI (must match the one used in authorization request). */
    @JsonProperty("redirect_uri")
    val redirectUri: String? = null,
    
    /** Client identifier. */
    @JsonProperty("client_id")
    val clientId: String? = null,
    
    /** Client secret (for confidential clients). */
    @JsonProperty("client_secret")
    val clientSecret: String? = null,
    
    /** PKCE code verifier. */
    @JsonProperty("code_verifier")
    val codeVerifier: String? = null,
    
    /** Refresh token (for refresh_token grant). */
    @JsonProperty("refresh_token")
    val refreshToken: String? = null,
    
    /** Requested scope (for refresh_token grant, must be subset of original). */
    val scope: String? = null,
    
    /** Username (for password grant - deprecated). */
    val username: String? = null,
    
    /** Password (for password grant - deprecated). */
    val password: String? = null
) {
    companion object {
        const val GRANT_TYPE_AUTHORIZATION_CODE = "authorization_code"
        const val GRANT_TYPE_REFRESH_TOKEN = "refresh_token"
        const val GRANT_TYPE_CLIENT_CREDENTIALS = "client_credentials"
        const val GRANT_TYPE_PASSWORD = "password" // Deprecated, avoid using
    }
}

/**
 * Token response from the token endpoint.
 * 
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc6749#section-5.1">RFC 6749 Section 5.1</a>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class TokenResponse(
    /** The access token issued by the authorization server. */
    @JsonProperty("access_token")
    val accessToken: String,
    
    /** The type of token (typically "Bearer"). */
    @JsonProperty("token_type")
    val tokenType: String = "Bearer",
    
    /** The lifetime of the access token in seconds. */
    @JsonProperty("expires_in")
    val expiresIn: Long,
    
    /** The refresh token (if issued). */
    @JsonProperty("refresh_token")
    val refreshToken: String? = null,
    
    /** The scope of the access token. */
    val scope: String? = null,
    
    /** ID token (OIDC only). */
    @JsonProperty("id_token")
    val idToken: String? = null
)

/**
 * User info response from the userinfo endpoint (OIDC).
 * 
 * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html#UserInfoResponse">OIDC UserInfo Response</a>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class UserInfoResponse(
    /** Subject identifier (unique user ID). */
    val sub: String,
    
    /** User's preferred username. */
    @JsonProperty("preferred_username")
    val preferredUsername: String? = null,
    
    /** User's email address. */
    val email: String? = null,
    
    /** Whether email is verified. */
    @JsonProperty("email_verified")
    val emailVerified: Boolean? = null,
    
    /** User's full name. */
    val name: String? = null,
    
    /** User's given (first) name. */
    @JsonProperty("given_name")
    val givenName: String? = null,
    
    /** User's family (last) name. */
    @JsonProperty("family_name")
    val familyName: String? = null,
    
    /** User's locale. */
    val locale: String? = null,
    
    /** Time user info was last updated. */
    @JsonProperty("updated_at")
    val updatedAt: Long? = null
)

/**
 * Authorization code stored during the authorization flow.
 * This is an internal DTO, not exposed via API.
 */
data class StoredAuthorizationCode(
    /** The authorization code value. */
    val code: String,
    
    /** Client ID that requested authorization. */
    val clientId: String,
    
    /** User who authorized the request. */
    val userId: Long,
    
    /** Redirect URI used in the request. */
    val redirectUri: String,
    
    /** Granted scopes. */
    val scope: String?,
    
    /** PKCE code challenge. */
    val codeChallenge: String?,
    
    /** PKCE code challenge method. */
    val codeChallengeMethod: String?,
    
    /** Nonce for ID token (OIDC). */
    val nonce: String?,
    
    /** When the code was issued. */
    val issuedAt: Instant = Instant.now(),
    
    /** When the code expires. */
    val expiresAt: Instant = Instant.now().plusSeconds(600), // 10 minutes default
    
    /** Whether the code has been used (codes are single-use). */
    var used: Boolean = false
) {
    /** Check if the code has expired. */
    fun isExpired(): Boolean = Instant.now().isAfter(expiresAt)
    
    /** Check if the code is still valid (not expired and not used). */
    fun isValid(): Boolean = !isExpired() && !used
}

/**
 * JWKS (JSON Web Key Set) response.
 * 
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc7517">RFC 7517</a>
 */
data class JwksResponse(
    val keys: List<JwkKey>
)

/**
 * Individual JWK (JSON Web Key).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class JwkKey(
    /** Key type (e.g., "RSA", "EC"). */
    val kty: String,
    
    /** Intended use ("sig" for signature, "enc" for encryption). */
    val use: String? = "sig",
    
    /** Key ID. */
    val kid: String,
    
    /** Algorithm (e.g., "RS256"). */
    val alg: String? = null,
    
    /** RSA modulus (Base64URL encoded). */
    val n: String? = null,
    
    /** RSA exponent (Base64URL encoded). */
    val e: String? = null
)

/**
 * OpenID Connect Discovery document.
 * 
 * @see <a href="https://openid.net/specs/openid-connect-discovery-1_0.html">OIDC Discovery</a>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class OpenIdConfiguration(
    /** Issuer identifier. */
    val issuer: String,
    
    /** Authorization endpoint URL. */
    @JsonProperty("authorization_endpoint")
    val authorizationEndpoint: String,
    
    /** Token endpoint URL. */
    @JsonProperty("token_endpoint")
    val tokenEndpoint: String,
    
    /** UserInfo endpoint URL. */
    @JsonProperty("userinfo_endpoint")
    val userinfoEndpoint: String,
    
    /** JWKS URI. */
    @JsonProperty("jwks_uri")
    val jwksUri: String,
    
    /** Supported response types. */
    @JsonProperty("response_types_supported")
    val responseTypesSupported: List<String> = listOf("code"),
    
    /** Supported grant types. */
    @JsonProperty("grant_types_supported")
    val grantTypesSupported: List<String> = listOf(
        "authorization_code",
        "refresh_token"
    ),
    
    /** Supported subject types. */
    @JsonProperty("subject_types_supported")
    val subjectTypesSupported: List<String> = listOf("public"),
    
    /** Supported ID token signing algorithms. */
    @JsonProperty("id_token_signing_alg_values_supported")
    val idTokenSigningAlgValuesSupported: List<String> = listOf("RS256"),
    
    /** Supported scopes. */
    @JsonProperty("scopes_supported")
    val scopesSupported: List<String> = listOf("openid", "profile", "email"),
    
    /** Supported token endpoint authentication methods. */
    @JsonProperty("token_endpoint_auth_methods_supported")
    val tokenEndpointAuthMethodsSupported: List<String> = listOf(
        "client_secret_basic",
        "client_secret_post"
    ),
    
    /** Supported code challenge methods. */
    @JsonProperty("code_challenge_methods_supported")
    val codeChallengeMethodsSupported: List<String> = listOf("S256", "plain"),
    
    /** Supported claims. */
    @JsonProperty("claims_supported")
    val claimsSupported: List<String> = listOf(
        "sub", "iss", "aud", "exp", "iat",
        "name", "email", "email_verified", "preferred_username"
    )
)
