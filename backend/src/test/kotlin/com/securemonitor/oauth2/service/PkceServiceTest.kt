package com.securemonitor.oauth2.service

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.assertThrows
import org.assertj.core.api.Assertions.assertThat
import com.securemonitor.oauth2.exception.OAuth2Exception

/**
 * Test suite for PKCE (Proof Key for Code Exchange) service.
 * 
 * PKCE is a security extension to OAuth 2.0 that prevents authorization code
 * interception attacks. It's required for public clients and recommended for
 * all OAuth 2.0 clients.
 * 
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc7636">RFC 7636</a>
 */
@DisplayName("PkceService")
class PkceServiceTest {

    private lateinit var pkceService: PkceService

    @BeforeEach
    fun setUp() {
        pkceService = PkceService()
    }

    @Nested
    @DisplayName("generateCodeVerifier")
    inner class GenerateCodeVerifier {

        @Test
        @DisplayName("should generate a code verifier with valid length (43-128 characters)")
        fun shouldGenerateValidLength() {
            val verifier = pkceService.generateCodeVerifier()
            
            assertThat(verifier.length).isBetween(43, 128)
        }

        @Test
        @DisplayName("should generate a code verifier using only valid characters (A-Z, a-z, 0-9, -._~)")
        fun shouldUseOnlyValidCharacters() {
            val verifier = pkceService.generateCodeVerifier()
            val validPattern = Regex("^[A-Za-z0-9\\-._~]+$")
            
            assertThat(verifier).matches(validPattern.toPattern())
        }

        @Test
        @DisplayName("should generate unique verifiers on each call")
        fun shouldGenerateUniqueVerifiers() {
            val verifiers = (1..100).map { pkceService.generateCodeVerifier() }.toSet()
            
            assertThat(verifiers).hasSize(100)
        }
    }

    @Nested
    @DisplayName("generateCodeChallenge")
    inner class GenerateCodeChallenge {

        @Test
        @DisplayName("should generate S256 challenge from verifier")
        fun shouldGenerateS256Challenge() {
            // Known test vector from RFC 7636 Appendix B
            val verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
            val expectedChallenge = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM"
            
            val challenge = pkceService.generateCodeChallenge(verifier, "S256")
            
            assertThat(challenge).isEqualTo(expectedChallenge)
        }

        @Test
        @DisplayName("should support plain method (for testing only)")
        fun shouldSupportPlainMethod() {
            val verifier = "test-verifier-string"
            
            val challenge = pkceService.generateCodeChallenge(verifier, "plain")
            
            assertThat(challenge).isEqualTo(verifier)
        }

        @Test
        @DisplayName("should throw exception for unsupported method")
        fun shouldThrowForUnsupportedMethod() {
            val verifier = "test-verifier"
            
            val exception = assertThrows<OAuth2Exception> {
                pkceService.generateCodeChallenge(verifier, "MD5")
            }
            
            assertThat(exception.error).isEqualTo("invalid_request")
            assertThat(exception.errorDescription).contains("Unsupported code challenge method")
        }
    }

    @Nested
    @DisplayName("verifyCodeChallenge")
    inner class VerifyCodeChallenge {

        @Test
        @DisplayName("should return true when S256 challenge matches verifier")
        fun shouldVerifyValidS256Challenge() {
            val verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
            val challenge = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM"
            
            val result = pkceService.verifyCodeChallenge(verifier, challenge, "S256")
            
            assertThat(result).isTrue()
        }

        @Test
        @DisplayName("should return false when S256 challenge does not match")
        fun shouldRejectInvalidS256Challenge() {
            val verifier = "wrong-verifier"
            val challenge = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM"
            
            val result = pkceService.verifyCodeChallenge(verifier, challenge, "S256")
            
            assertThat(result).isFalse()
        }

        @Test
        @DisplayName("should return true when plain challenge matches verifier")
        fun shouldVerifyValidPlainChallenge() {
            val verifier = "my-plain-verifier"
            val challenge = "my-plain-verifier"
            
            val result = pkceService.verifyCodeChallenge(verifier, challenge, "plain")
            
            assertThat(result).isTrue()
        }

        @Test
        @DisplayName("should use timing-safe comparison to prevent timing attacks")
        fun shouldUseTimingSafeComparison() {
            // This test verifies the behavior, actual timing-safe implementation
            // is verified through code review
            val verifier = pkceService.generateCodeVerifier()
            val challenge = pkceService.generateCodeChallenge(verifier, "S256")
            
            val result = pkceService.verifyCodeChallenge(verifier, challenge, "S256")
            
            assertThat(result).isTrue()
        }
    }

    @Nested
    @DisplayName("validateCodeVerifier")
    inner class ValidateCodeVerifier {

        @Test
        @DisplayName("should accept valid code verifier")
        fun shouldAcceptValidVerifier() {
            val verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
            
            // Should not throw
            pkceService.validateCodeVerifier(verifier)
        }

        @Test
        @DisplayName("should reject code verifier shorter than 43 characters")
        fun shouldRejectTooShortVerifier() {
            val verifier = "too-short"
            
            val exception = assertThrows<OAuth2Exception> {
                pkceService.validateCodeVerifier(verifier)
            }
            
            assertThat(exception.error).isEqualTo("invalid_request")
            assertThat(exception.errorDescription).contains("43")
        }

        @Test
        @DisplayName("should reject code verifier longer than 128 characters")
        fun shouldRejectTooLongVerifier() {
            val verifier = "a".repeat(129)
            
            val exception = assertThrows<OAuth2Exception> {
                pkceService.validateCodeVerifier(verifier)
            }
            
            assertThat(exception.error).isEqualTo("invalid_request")
            assertThat(exception.errorDescription).contains("128")
        }

        @Test
        @DisplayName("should reject code verifier with invalid characters")
        fun shouldRejectInvalidCharacters() {
            val verifier = "valid-start-but-has-invalid-char-!" + "a".repeat(30)
            
            val exception = assertThrows<OAuth2Exception> {
                pkceService.validateCodeVerifier(verifier)
            }
            
            assertThat(exception.error).isEqualTo("invalid_request")
            assertThat(exception.errorDescription).contains("invalid characters")
        }
    }
}
