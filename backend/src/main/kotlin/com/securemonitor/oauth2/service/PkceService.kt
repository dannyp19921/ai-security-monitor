package com.securemonitor.oauth2.service

import com.securemonitor.oauth2.exception.OAuth2Exception
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Service for handling PKCE (Proof Key for Code Exchange) operations.
 * 
 * PKCE is a security extension to OAuth 2.0 designed to prevent authorization
 * code interception attacks. It is required for public clients (e.g., mobile apps,
 * SPAs) and recommended for all OAuth 2.0 clients.
 * 
 * The flow works as follows:
 * 1. Client generates a random `code_verifier` (43-128 characters)
 * 2. Client computes `code_challenge` = BASE64URL(SHA256(code_verifier))
 * 3. Client includes `code_challenge` and `code_challenge_method` in /authorize request
 * 4. Server stores the challenge with the authorization code
 * 5. Client includes `code_verifier` in /token request
 * 6. Server verifies that SHA256(code_verifier) matches stored challenge
 * 
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc7636">RFC 7636 - PKCE</a>
 */
@Service
class PkceService {

    companion object {
        /** Minimum length for code verifier as per RFC 7636 */
        const val CODE_VERIFIER_MIN_LENGTH = 43
        
        /** Maximum length for code verifier as per RFC 7636 */
        const val CODE_VERIFIER_MAX_LENGTH = 128
        
        /** Default length for generated code verifiers */
        const val CODE_VERIFIER_DEFAULT_LENGTH = 64
        
        /** Valid characters for code verifier (unreserved URI characters) */
        private val CODE_VERIFIER_CHARACTERS = 
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"
        
        /** Pattern for validating code verifier characters */
        private val CODE_VERIFIER_PATTERN = Regex("^[A-Za-z0-9\\-._~]+$")
        
        /** Supported code challenge methods */
        val SUPPORTED_METHODS = setOf("S256", "plain")
        
        /** Recommended code challenge method */
        const val RECOMMENDED_METHOD = "S256"
    }

    private val secureRandom = SecureRandom()

    /**
     * Generates a cryptographically secure code verifier.
     * 
     * The verifier is a high-entropy cryptographic random string using the
     * unreserved characters [A-Z] / [a-z] / [0-9] / "-" / "." / "_" / "~".
     * 
     * @param length The desired length (default: 64, must be 43-128)
     * @return A random code verifier string
     * @throws OAuth2Exception if length is outside valid range
     */
    fun generateCodeVerifier(length: Int = CODE_VERIFIER_DEFAULT_LENGTH): String {
        require(length in CODE_VERIFIER_MIN_LENGTH..CODE_VERIFIER_MAX_LENGTH) {
            "Code verifier length must be between $CODE_VERIFIER_MIN_LENGTH and $CODE_VERIFIER_MAX_LENGTH"
        }
        
        return buildString(length) {
            repeat(length) {
                val randomIndex = secureRandom.nextInt(CODE_VERIFIER_CHARACTERS.length)
                append(CODE_VERIFIER_CHARACTERS[randomIndex])
            }
        }
    }

    /**
     * Generates a code challenge from a code verifier.
     * 
     * For S256 method: BASE64URL(SHA256(ASCII(code_verifier)))
     * For plain method: code_challenge = code_verifier (not recommended)
     * 
     * @param codeVerifier The code verifier to transform
     * @param method The challenge method ("S256" recommended, "plain" for testing only)
     * @return The computed code challenge
     * @throws OAuth2Exception if the method is not supported
     */
    fun generateCodeChallenge(codeVerifier: String, method: String): String {
        return when (method.uppercase()) {
            "S256" -> computeS256Challenge(codeVerifier)
            "PLAIN" -> codeVerifier
            else -> throw OAuth2Exception.invalidRequest(
                "Unsupported code challenge method: $method. Supported methods: $SUPPORTED_METHODS"
            )
        }
    }

    /**
     * Verifies that a code verifier matches the stored code challenge.
     * 
     * This method uses timing-safe comparison to prevent timing attacks.
     * 
     * @param codeVerifier The code verifier from the token request
     * @param codeChallenge The code challenge stored during authorization
     * @param method The code challenge method used
     * @return true if verification succeeds, false otherwise
     */
    fun verifyCodeChallenge(codeVerifier: String, codeChallenge: String, method: String): Boolean {
        return try {
            val computedChallenge = generateCodeChallenge(codeVerifier, method)
            timingSafeEquals(computedChallenge, codeChallenge)
        } catch (e: OAuth2Exception) {
            false
        }
    }

    /**
     * Validates that a code verifier meets RFC 7636 requirements.
     * 
     * Requirements:
     * - Length: 43-128 characters
     * - Characters: [A-Z] / [a-z] / [0-9] / "-" / "." / "_" / "~"
     * 
     * @param codeVerifier The code verifier to validate
     * @throws OAuth2Exception if validation fails
     */
    fun validateCodeVerifier(codeVerifier: String) {
        if (codeVerifier.length < CODE_VERIFIER_MIN_LENGTH) {
            throw OAuth2Exception.invalidRequest(
                "Code verifier must be at least $CODE_VERIFIER_MIN_LENGTH characters"
            )
        }
        
        if (codeVerifier.length > CODE_VERIFIER_MAX_LENGTH) {
            throw OAuth2Exception.invalidRequest(
                "Code verifier must not exceed $CODE_VERIFIER_MAX_LENGTH characters"
            )
        }
        
        if (!CODE_VERIFIER_PATTERN.matches(codeVerifier)) {
            throw OAuth2Exception.invalidRequest(
                "Code verifier contains invalid characters. " +
                "Allowed: A-Z, a-z, 0-9, '-', '.', '_', '~'"
            )
        }
    }

    /**
     * Computes the S256 code challenge.
     * 
     * S256: BASE64URL(SHA256(ASCII(code_verifier)))
     * Note: BASE64URL encoding omits padding ('=') as per RFC 7636.
     */
    private fun computeS256Challenge(codeVerifier: String): String {
        val bytes = codeVerifier.toByteArray(Charsets.US_ASCII)
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    /**
     * Performs timing-safe comparison of two strings.
     * 
     * This prevents timing attacks by ensuring the comparison takes
     * constant time regardless of where the strings differ.
     */
    private fun timingSafeEquals(a: String, b: String): Boolean {
        val aBytes = a.toByteArray(Charsets.UTF_8)
        val bBytes = b.toByteArray(Charsets.UTF_8)
        
        return MessageDigest.isEqual(aBytes, bBytes)
    }
}
