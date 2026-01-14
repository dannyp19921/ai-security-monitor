package com.securemonitor.oauth2.service

import com.securemonitor.oauth2.dto.TokenRequest
import com.securemonitor.oauth2.exception.OAuth2Exception
import com.securemonitor.oauth2.model.AuthorizationCode
import com.securemonitor.oauth2.model.OAuth2Client
import com.securemonitor.oauth2.repository.AuthorizationCodeRepository
import com.securemonitor.oauth2.repository.OAuth2ClientRepository
import com.securemonitor.service.AuditService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import org.assertj.core.api.Assertions.assertThat
import java.time.Instant
import java.util.Optional

/**
 * Test suite for TokenService.
 * 
 * Tests the token endpoint logic including:
 * - Authorization code exchange
 * - PKCE verification
 * - Token generation
 * - Refresh token flow
 */
@ExtendWith(MockitoExtension::class)
@DisplayName("TokenService")
class TokenServiceTest {

    @Mock
    private lateinit var clientRepository: OAuth2ClientRepository
    
    @Mock
    private lateinit var codeRepository: AuthorizationCodeRepository
    
    @Mock
    private lateinit var pkceService: PkceService
    
    @Mock
    private lateinit var auditService: AuditService

    private lateinit var tokenService: TokenService

    private val testClient = OAuth2Client(
        id = 1L,
        clientId = "test-client",
        clientSecretHash = null,
        clientName = "Test Client",
        confidential = false,
        redirectUris = "http://localhost:3000/callback",
        allowedScopes = "openid profile email",
        allowedGrantTypes = "authorization_code refresh_token",
        requirePkce = true,
        accessTokenLifetimeSeconds = 3600,
        refreshTokenLifetimeSeconds = 86400
    )

    private val testAuthCode = AuthorizationCode(
        id = 1L,
        code = "valid-auth-code",
        clientId = "test-client",
        userId = 42L,
        username = "testuser",
        redirectUri = "http://localhost:3000/callback",
        scope = "openid profile",
        codeChallenge = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
        codeChallengeMethod = "S256",
        nonce = "nonce123",
        issuedAt = Instant.now(),
        expiresAt = Instant.now().plusSeconds(600),
        used = false
    )

    @BeforeEach
    fun setUp() {
        tokenService = TokenService(
            clientRepository = clientRepository,
            codeRepository = codeRepository,
            pkceService = pkceService,
            auditService = auditService,
            issuer = "http://localhost:8080",
            accessTokenLifetimeSeconds = 3600,
            refreshTokenLifetimeSeconds = 86400
        )
    }

    @Nested
    @DisplayName("exchangeAuthorizationCode")
    inner class ExchangeAuthorizationCode {

        @Test
        @DisplayName("should exchange valid authorization code for tokens")
        fun shouldExchangeValidCode() {
            val request = TokenRequest(
                grantType = "authorization_code",
                code = "valid-auth-code",
                redirectUri = "http://localhost:3000/callback",
                clientId = "test-client",
                codeVerifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
            )
            
            whenever(codeRepository.findValidCode(eq("valid-auth-code"), any()))
                .thenReturn(Optional.of(testAuthCode))
            whenever(clientRepository.findByClientId("test-client"))
                .thenReturn(Optional.of(testClient))
            whenever(pkceService.verifyCodeChallenge(
                eq("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"),
                eq("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM"),
                eq("S256")
            )).thenReturn(true)
            whenever(codeRepository.save(any<AuthorizationCode>()))
                .thenAnswer { it.arguments[0] }
            
            val response = tokenService.exchangeAuthorizationCode(request)
            
            assertThat(response.accessToken).isNotBlank()
            assertThat(response.tokenType).isEqualTo("Bearer")
            assertThat(response.expiresIn).isEqualTo(3600)
            assertThat(response.refreshToken).isNotBlank()
            assertThat(response.scope).isEqualTo("openid profile")
            
            // Verify code was marked as used
            verify(codeRepository).save(argThat { used })
        }

        @Test
        @DisplayName("should reject expired authorization code")
        fun shouldRejectExpiredCode() {
            val request = TokenRequest(
                grantType = "authorization_code",
                code = "expired-code",
                redirectUri = "http://localhost:3000/callback",
                clientId = "test-client"
            )
            
            whenever(codeRepository.findValidCode(eq("expired-code"), any()))
                .thenReturn(Optional.empty())
            
            val exception = assertThrows<OAuth2Exception> {
                tokenService.exchangeAuthorizationCode(request)
            }
            
            assertThat(exception.error).isEqualTo("invalid_grant")
        }

        @Test
        @DisplayName("should reject already used authorization code")
        fun shouldRejectUsedCode() {
            val usedCode = testAuthCode.copy(used = true)
            val request = TokenRequest(
                grantType = "authorization_code",
                code = "used-code",
                redirectUri = "http://localhost:3000/callback",
                clientId = "test-client"
            )
            
            // findValidCode already excludes used codes
            whenever(codeRepository.findValidCode(eq("used-code"), any()))
                .thenReturn(Optional.empty())
            
            val exception = assertThrows<OAuth2Exception> {
                tokenService.exchangeAuthorizationCode(request)
            }
            
            assertThat(exception.error).isEqualTo("invalid_grant")
        }

        @Test
        @DisplayName("should reject mismatched redirect_uri")
        fun shouldRejectMismatchedRedirectUri() {
            val request = TokenRequest(
                grantType = "authorization_code",
                code = "valid-auth-code",
                redirectUri = "http://different.com/callback", // different from code
                clientId = "test-client",
                codeVerifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
            )
            
            whenever(codeRepository.findValidCode(eq("valid-auth-code"), any()))
                .thenReturn(Optional.of(testAuthCode))
            
            val exception = assertThrows<OAuth2Exception> {
                tokenService.exchangeAuthorizationCode(request)
            }
            
            assertThat(exception.error).isEqualTo("invalid_grant")
            assertThat(exception.errorDescription).contains("redirect_uri")
        }

        @Test
        @DisplayName("should reject mismatched client_id")
        fun shouldRejectMismatchedClientId() {
            val request = TokenRequest(
                grantType = "authorization_code",
                code = "valid-auth-code",
                redirectUri = "http://localhost:3000/callback",
                clientId = "different-client", // different from code
                codeVerifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
            )
            
            whenever(codeRepository.findValidCode(eq("valid-auth-code"), any()))
                .thenReturn(Optional.of(testAuthCode))
            
            val exception = assertThrows<OAuth2Exception> {
                tokenService.exchangeAuthorizationCode(request)
            }
            
            assertThat(exception.error).isEqualTo("invalid_grant")
            assertThat(exception.errorDescription).contains("client")
        }

        @Test
        @DisplayName("should reject invalid PKCE verifier")
        fun shouldRejectInvalidPkceVerifier() {
            val request = TokenRequest(
                grantType = "authorization_code",
                code = "valid-auth-code",
                redirectUri = "http://localhost:3000/callback",
                clientId = "test-client",
                codeVerifier = "wrong-verifier"
            )
            
            whenever(codeRepository.findValidCode(eq("valid-auth-code"), any()))
                .thenReturn(Optional.of(testAuthCode))
            whenever(clientRepository.findByClientId("test-client"))
                .thenReturn(Optional.of(testClient))
            whenever(pkceService.verifyCodeChallenge(
                eq("wrong-verifier"),
                eq("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM"),
                eq("S256")
            )).thenReturn(false)
            
            val exception = assertThrows<OAuth2Exception> {
                tokenService.exchangeAuthorizationCode(request)
            }
            
            assertThat(exception.error).isEqualTo("invalid_grant")
            assertThat(exception.errorDescription).contains("PKCE")
        }

        @Test
        @DisplayName("should reject missing PKCE verifier when required")
        fun shouldRejectMissingPkceVerifier() {
            val request = TokenRequest(
                grantType = "authorization_code",
                code = "valid-auth-code",
                redirectUri = "http://localhost:3000/callback",
                clientId = "test-client",
                codeVerifier = null // missing
            )
            
            whenever(codeRepository.findValidCode(eq("valid-auth-code"), any()))
                .thenReturn(Optional.of(testAuthCode))
            whenever(clientRepository.findByClientId("test-client"))
                .thenReturn(Optional.of(testClient))
            
            val exception = assertThrows<OAuth2Exception> {
                tokenService.exchangeAuthorizationCode(request)
            }
            
            assertThat(exception.error).isEqualTo("invalid_grant")
            assertThat(exception.errorDescription).contains("code_verifier")
        }
    }

    @Nested
    @DisplayName("validateGrantType")
    inner class ValidateGrantType {

        @Test
        @DisplayName("should accept supported grant types")
        fun shouldAcceptSupportedGrantTypes() {
            // Should not throw
            tokenService.validateGrantType("authorization_code")
            tokenService.validateGrantType("refresh_token")
        }

        @Test
        @DisplayName("should reject unsupported grant types")
        fun shouldRejectUnsupportedGrantTypes() {
            val exception = assertThrows<OAuth2Exception> {
                tokenService.validateGrantType("password")
            }
            
            assertThat(exception.error).isEqualTo("unsupported_grant_type")
        }
    }
}
